package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillableDayRules.DayRule;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillableDayRules.RuleVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contract overrides of the billable-day rule, and the holiday list some of them use. Accrual asks {@link #book} which days a client's storage bills. */
@Service
@RequiredArgsConstructor
public class WhseBillingDayService {

    private final JdbcTemplate jdbc;

    public record Rule(UUID id, UUID clientId, String clientName, String dayRule, String label, String minimumBasis, String minimumBasisLabel, LocalDate effectiveFrom, String note, Instant createdAt) {}
    public record Holiday(LocalDate date, String name) {}

    /** What accrual needs for a tenant: the rule versions and holidays, loaded once per run. */
    public static final class Book {
        private final List<RuleVersion> versions;
        private final Set<LocalDate> holidays;
        Book(List<RuleVersion> versions, Set<LocalDate> holidays) { this.versions = versions; this.holidays = holidays; }
        /** For tests and callers that already hold the versions. */
        public static Book of(List<RuleVersion> versions, Set<LocalDate> holidays) { return new Book(versions, holidays); }
        public boolean billable(UUID clientId, LocalDate date) {
            return WhseBillableDayRules.isBillable(WhseBillableDayRules.ruleFor(versions, clientId, date), date, holidays);
        }
        /** Days a minimum charge is pro-rated over for an invoice covering the days after {@code after} through {@code through}. */
        public long minimumDays(UUID clientId, LocalDate after, LocalDate through) {
            return WhseBillableDayRules.minimumDays(versions, clientId, after, through, holidays);
        }
    }

    @Transactional(readOnly = true)
    public Book book(TenantId tenantId) {
        List<RuleVersion> v = jdbc.query("SELECT client_id, day_rule, minimum_basis, effective_from, created_at FROM whse_billing_day_rules WHERE tenant_id = ?",
                (rs, i) -> new RuleVersion((UUID) rs.getObject("client_id"), WhseBillableDayRules.parse(rs.getString("day_rule")), rs.getDate("effective_from").toLocalDate(), rs.getTimestamp("created_at").toInstant(), WhseBillableDayRules.parseBasis(rs.getString("minimum_basis"))),
                tenantId.getValue());
        Set<LocalDate> h = new HashSet<>(jdbc.query("SELECT holiday_date FROM whse_billing_holidays WHERE tenant_id = ?", (rs, i) -> rs.getDate(1).toLocalDate(), tenantId.getValue()));
        return new Book(v, h);
    }

    @Transactional(readOnly = true)
    public List<Rule> rules(TenantId tenantId) {
        return jdbc.query("SELECT r.id, r.client_id, c.trading_name, r.day_rule, r.minimum_basis, r.effective_from, r.note, r.created_at FROM whse_billing_day_rules r LEFT JOIN whse_clients c ON c.id = r.client_id "
                + "WHERE r.tenant_id = ? ORDER BY r.effective_from DESC, r.created_at DESC",
                (rs, i) -> new Rule((UUID) rs.getObject("id"), (UUID) rs.getObject("client_id"), rs.getString("trading_name"), rs.getString("day_rule"),
                        WhseBillableDayRules.parse(rs.getString("day_rule")).label, rs.getString("minimum_basis"), WhseBillableDayRules.parseBasis(rs.getString("minimum_basis")).label, rs.getDate("effective_from").toLocalDate(), rs.getString("note"), rs.getTimestamp("created_at").toInstant()),
                tenantId.getValue());
    }

    /** clientId null sets the warehouse default. */
    @Transactional
    public UUID addRule(TenantId tenantId, UUID clientId, String dayRule, LocalDate effectiveFrom, String note, UUID actor, LocalDate today) {
        return addRule(tenantId, clientId, dayRule, null, effectiveFrom, note, actor, today);
    }

    /** minimumBasis null means CALENDAR_DAYS, the standard. */
    @Transactional
    public UUID addRule(TenantId tenantId, UUID clientId, String dayRule, String minimumBasis, LocalDate effectiveFrom, String note, UUID actor, LocalDate today) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        DayRule rule = WhseBillableDayRules.parse(dayRule);
        WhseBillableDayRules.MinimumBasis basis = WhseBillableDayRules.parseBasis(minimumBasis);
        WhseBillableDayRules.checkEffectiveFrom(effectiveFrom, today);
        if (clientId != null) {
            Integer c = jdbc.queryForObject("SELECT count(*) FROM whse_clients WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", Integer.class, tenantId.getValue(), clientId);
            if (c == null || c == 0) throw new IllegalArgumentException("That client account was not found.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_billing_day_rules (id, tenant_id, client_id, day_rule, minimum_basis, effective_from, note, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), clientId, rule.name(), basis.name(), effectiveFrom, note == null || note.isBlank() ? null : note.strip(), actor);
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, 'WHSE_BILLABLE_DAY_RULE_SET', 'WHSE_BILLING_DAY_RULE', ?, jsonb_build_object('rule', ?::text, 'minimumBasis', ?::text, 'effectiveFrom', ?::text, 'clientId', ?::text))",
                tenantId.getValue(), actor, id, rule.name(), basis.name(), effectiveFrom.toString(), clientId == null ? "" : clientId.toString());
        return id;
    }

    @Transactional(readOnly = true)
    public List<Holiday> holidays(TenantId tenantId) {
        return jdbc.query("SELECT holiday_date, name FROM whse_billing_holidays WHERE tenant_id = ? ORDER BY holiday_date", (rs, i) -> new Holiday(rs.getDate(1).toLocalDate(), rs.getString(2)), tenantId.getValue());
    }

    @Transactional
    public void addHoliday(TenantId tenantId, LocalDate date, String name, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        if (date == null) throw new IllegalArgumentException("Choose the holiday date.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the holiday.");
        if (name.strip().length() > 80) throw new IllegalArgumentException("The holiday name can be at most 80 characters.");
        jdbc.update("INSERT INTO whse_billing_holidays (tenant_id, holiday_date, name, created_by) VALUES (?,?,?,?) ON CONFLICT (tenant_id, holiday_date) DO UPDATE SET name = EXCLUDED.name",
                tenantId.getValue(), date, name.strip(), actor);
    }

    @Transactional
    public void removeHoliday(TenantId tenantId, LocalDate date, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        jdbc.update("DELETE FROM whse_billing_holidays WHERE tenant_id = ? AND holiday_date = ?", tenantId.getValue(), date);
    }
}
