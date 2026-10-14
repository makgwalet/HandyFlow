package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.ConsolidationRules.*;
import za.co.handyflow.platform.accounting.dto.GroupDtos;
import za.co.handyflow.platform.accounting.dto.GroupDtos.Entity;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Consolidated statements for a group (finance F26). Only the group's parent can run it, and only account TOTALS of companies
 * that accepted the invitation are read: never journal lines, descriptions or counterparties.
 */
@Service
public class ConsolidationService {

    private final JdbcTemplate jdbc;
    private final GroupService groups;

    public ConsolidationService(JdbcTemplate jdbc, GroupService groups) {
        this.jdbc = jdbc;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public GroupDtos.Report report(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new HandyFlowException("Choose a period that ends on or after it starts.", HttpStatus.BAD_REQUEST, "GROUP_REFUSED");
        GroupService.GroupRow g = groups.requireParent(tenantId);
        var members = groups.members(g.id());
        // Parent first, so the first column is always the reporting company.
        List<GroupDtos.Member> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing((GroupDtos.Member m) -> !m.tenantId().equals(tenantId.getValue())).thenComparing(GroupDtos.Member::name));
        List<UUID> ids = ordered.stream().map(GroupDtos.Member::tenantId).toList();
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));

        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(from)); args.add(Date.valueOf(to));    // P&L accounts: this period
        args.add(Date.valueOf(from));                                // earlier P&L (folded into retained earnings)
        args.addAll(ids);
        args.add(Date.valueOf(to));
        List<Bal> balances = new ArrayList<>();
        Map<UUID, BigDecimal> prior = new HashMap<>();
        jdbc.query("""
                SELECT a.tenant_id, a.account_code, a.account_name, a.account_type,
                       SUM(CASE WHEN a.account_type IN ('INCOME','EXPENSE')
                                THEN CASE WHEN e.entry_date BETWEEN ? AND ? THEN l.debit_amount - l.credit_amount ELSE 0 END
                                ELSE l.debit_amount - l.credit_amount END),
                       SUM(CASE WHEN a.account_type IN ('INCOME','EXPENSE') AND e.entry_date < ? THEN l.debit_amount - l.credit_amount ELSE 0 END)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE a.tenant_id IN (%s) AND e.tenant_id = a.tenant_id AND l.tenant_id = a.tenant_id
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date <= ?
                 GROUP BY a.tenant_id, a.account_code, a.account_name, a.account_type""".formatted(marks),
                rs -> {
                    UUID t = rs.getObject(1, UUID.class);
                    balances.add(new Bal(t, rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5)));
                    prior.merge(t, rs.getBigDecimal(6), BigDecimal::add);
                }, args.toArray());
        prior.forEach((t, v) -> { if (v.signum() != 0) balances.add(new Bal(t, "RE-PRIOR", "Retained earnings of earlier periods", "EQUITY", v)); });

        Map<Key, MapTo> mapping = new HashMap<>();
        groups.mappings(tenantId).forEach(m -> mapping.put(new Key(m.tenantId(), m.accountCode()), new MapTo(m.groupCode(), m.groupName())));
        List<Elim> elims = groups.eliminations(tenantId).stream()
                .map(e -> new Elim(e.name(), e.tenantAId(), e.accountCodeA(), e.tenantBId(), e.accountCodeB())).toList();

        Result r = ConsolidationRules.consolidate(ids, balances, mapping, elims);

        List<String> warnings = new ArrayList<>();
        Map<UUID, BigDecimal> own = new LinkedHashMap<>();
        ordered.forEach(m -> own.put(m.tenantId(), m.ownershipPercent()));
        for (UUID t : ConsolidationRules.partlyOwned(own)) {
            String name = ordered.stream().filter(m -> m.tenantId().equals(t)).findFirst().map(GroupDtos.Member::name).orElse("A company");
            warnings.add(name + " is owned for " + own.get(t).stripTrailingZeros().toPlainString() + "%. It is included at 100%; the minority share is not worked out.");
        }
        if (ordered.size() == 1) warnings.add("No other company has joined the group yet.");
        for (IcCheck c : r.checks()) if (c.difference().signum() != 0)
            warnings.add("Intercompany rule \"" + c.name() + "\" does not agree: the two sides differ by " + c.difference().abs().toPlainString() + ". Find out why before relying on the consolidation.");
        if (r.balanceDifference().signum() != 0)
            warnings.add("The statements do not balance (difference " + r.balanceDifference().toPlainString() + "). Check the account mappings.");

        List<Entity> entities = ordered.stream().map(m -> new Entity(m.tenantId(), m.name(), m.ownershipPercent())).toList();
        List<GroupDtos.Line> lines = r.rows().stream().map(x -> new GroupDtos.Line(x.code(), x.name(), x.type(), x.amounts(), x.elimination(), x.consolidated())).toList();
        List<GroupDtos.Check> checks = r.checks().stream().map(c -> new GroupDtos.Check(c.name(), c.sideA(), c.sideB(), c.difference())).toList();
        return new GroupDtos.Report(from, to, entities, lines, checks, r.income(), r.expenses(), r.profit(), r.assets(), r.liabilities(), r.equity(), r.balanceDifference(), warnings);
    }
}
