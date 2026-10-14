package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.TagRules.Line;
import za.co.handyflow.platform.accounting.dto.ProfitabilityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.util.*;

/**
 * Rules that tag journal lines with a cost centre automatically (finance F28), on new journals and, when asked, on past ones.
 * Tagging only adds a dimension: no amount, account or date changes, so balances and the trial balance cannot move. Past lines
 * tagged this way are logged.
 */
@Service
public class TagRuleService {

    static final int RETRO_LIMIT = 5000;
    private final JdbcTemplate jdbc;

    public TagRuleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "TAG_RULE_REFUSED"); }

    @Transactional(readOnly = true)
    public List<Rule> list(TenantId tenantId) {
        return jdbc.query("""
                SELECT r.id, r.name, r.priority, r.account_code, r.text_contains, r.entry_type, r.cost_centre_id, c.code || ' — ' || c.name, r.active
                  FROM acc_tag_rules r JOIN acc_cost_centres c ON c.id = r.cost_centre_id
                 WHERE r.tenant_id = ? ORDER BY r.priority, r.name""",
                (rs, n) -> new Rule(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getObject(7, UUID.class), rs.getString(8), rs.getBoolean(9)), tenantId.getValue());
    }

    /** Active rules whose cost centre is still switched on, ready to match. */
    @Transactional(readOnly = true)
    public List<TagRules.Rule> activeRules(TenantId tenantId) {
        return jdbc.query("""
                SELECT r.id, r.name, r.priority, r.account_code, r.text_contains, r.entry_type, r.cost_centre_id
                  FROM acc_tag_rules r JOIN acc_cost_centres c ON c.id = r.cost_centre_id
                 WHERE r.tenant_id = ? AND r.active = TRUE AND c.active = TRUE""",
                (rs, n) -> new TagRules.Rule(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class)),
                tenantId.getValue());
    }

    private void requireCentre(TenantId tenantId, UUID id) {
        Integer n = id == null ? 0 : jdbc.queryForObject("SELECT COUNT(*) FROM acc_cost_centres WHERE id = ? AND tenant_id = ? AND active = TRUE", Integer.class, id, tenantId.getValue());
        if (n == null || n == 0) throw bad("That cost centre does not exist or is switched off.");
    }

    @Transactional
    public Rule create(TenantId tenantId, RuleSave req, UUID by) {
        int priority = req.priority() == null ? 100 : req.priority();
        String problem = TagRules.saveProblem(req.name(), req.accountCode(), req.textContains(), req.entryType(), req.costCentreId(), priority);
        if (problem != null) throw bad(problem);
        requireCentre(tenantId, req.costCentreId());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM acc_tag_rules WHERE tenant_id = ?", Integer.class, tenantId.getValue());
        if (count != null && count >= TagRules.MAX_RULES) throw bad("At most " + TagRules.MAX_RULES + " rules. Remove ones you no longer need.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO acc_tag_rules (id, tenant_id, name, priority, account_code, text_contains, entry_type, cost_centre_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId.getValue(), req.name().trim(), priority, blank(req.accountCode()), blank(req.textContains()), blank(req.entryType()), req.costCentreId(), by);
        return list(tenantId).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @Transactional
    public List<Rule> setActive(TenantId tenantId, UUID id, boolean active) {
        int n = jdbc.update("UPDATE acc_tag_rules SET active = ? WHERE id = ? AND tenant_id = ?", active, id, tenantId.getValue());
        if (n == 0) throw new HandyFlowException("Rule not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return list(tenantId);
    }

    @Transactional
    public List<Rule> delete(TenantId tenantId, UUID id) {
        jdbc.update("DELETE FROM acc_tag_rules WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
        return list(tenantId);
    }

    /**
     * Previews, or applies, the rules to income and expense lines that have no cost centre, in posted journals dated in the period.
     * At most {@value #RETRO_LIMIT} lines per call; run it again for the rest.
     */
    @Transactional
    public ApplyResult retro(TenantId tenantId, ApplyRequest req, UUID by) {
        if (req.from() == null || req.to() == null || req.to().isBefore(req.from())) throw bad("Choose a period that ends on or after it starts.");
        List<TagRules.Rule> rules = activeRules(tenantId);
        if (rules.isEmpty()) throw bad("There are no active rules to apply.");
        Map<UUID, String> centreNames = new HashMap<>();
        jdbc.query("SELECT id, code FROM acc_cost_centres WHERE tenant_id = ?", rs -> { centreNames.put(rs.getObject(1, UUID.class), rs.getString(2)); }, tenantId.getValue());

        record Cand(UUID lineId, java.time.LocalDate date, String entryNumber, Line line) {}
        List<Cand> cands = jdbc.query("""
                SELECT l.id, e.entry_date, e.entry_number, a.account_code, a.account_type, l.description, e.description, e.reference, e.entry_type
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE l.tenant_id = ? AND e.tenant_id = ? AND l.cost_centre_id IS NULL AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?
                 ORDER BY e.entry_date, e.entry_number, l.sort_order LIMIT ?""",
                (rs, n) -> new Cand(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), rs.getString(3),
                        new Line(rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), null)),
                tenantId.getValue(), tenantId.getValue(), Date.valueOf(req.from()), Date.valueOf(req.to()), RETRO_LIMIT + 1);
        boolean limit = cands.size() > RETRO_LIMIT;
        if (limit) cands = cands.subList(0, RETRO_LIMIT);

        Map<String, Integer> byRule = new TreeMap<>();
        List<Sample> samples = new ArrayList<>();
        int matched = 0;
        for (Cand c : cands) {
            TagRules.Rule r = TagRules.pick(rules, c.line());
            if (r == null) continue;
            matched++;
            byRule.merge(r.name(), 1, Integer::sum);
            if (samples.size() < 20) samples.add(new Sample(c.date(), c.entryNumber(), c.line().accountCode(),
                    c.line().lineDescription() != null && !c.line().lineDescription().isBlank() ? c.line().lineDescription() : c.line().entryDescription(), r.name(), centreNames.get(r.costCentreId())));
            if (req.apply()) {
                int n = jdbc.update("UPDATE acc_journal_lines SET cost_centre_id = ? WHERE id = ? AND tenant_id = ? AND cost_centre_id IS NULL", r.costCentreId(), c.lineId(), tenantId.getValue());
                if (n == 1) jdbc.update("INSERT INTO acc_cost_centre_tag_log (tenant_id, line_id, cost_centre_id, rule_id, applied_by) VALUES (?, ?, ?, ?, ?)",
                        tenantId.getValue(), c.lineId(), r.costCentreId(), r.id(), by);
            }
        }
        return new ApplyResult(req.apply(), matched, limit, byRule, samples);
    }
}
