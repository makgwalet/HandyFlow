// security/application/internal/WorkingTimePolicyService.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.WorkingTimePolicy.Policy;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores working-time policies (append-only, effective-dated) and which category each guard is in. Always within the company. */
@Service
public class WorkingTimePolicyService {

    static final int MAX_LIST = 500;
    /** Optional so a service built by hand still works; without it South Africa's zone is used, never the server's. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private final JdbcTemplate jdbc;

    public WorkingTimePolicyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Policy map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Policy((UUID) rs.getObject("id"), rs.getString("category"), rs.getObject("effective_from", LocalDate.class),
                rs.getDouble("warn_at"), rs.getDouble("ordinary_weekly"), rs.getDouble("max_overtime_weekly"), rs.getDouble("max_daily"),
                rs.getDouble("max_daily_overtime"), rs.getDouble("min_rest_hours"), rs.getString("legal_basis"));
    }

    private static final String COLS = "id, category, effective_from, warn_at, ordinary_weekly, max_overtime_weekly, max_daily, max_daily_overtime, min_rest_hours, legal_basis";

    @Transactional(readOnly = true)
    public List<Policy> list(TenantId tenantId) {
        return jdbc.query("SELECT " + COLS + " FROM security_working_time_policies WHERE tenant_id = ? ORDER BY category, effective_from DESC LIMIT " + MAX_LIST,
                (rs, i) -> map(rs), tenantId.getValue());
    }

    /** Every policy that has started on or before the day (enough to choose the one in force for a week starting then). */
    @Transactional(readOnly = true)
    public List<Policy> startedBy(TenantId tenantId, LocalDate day) {
        return jdbc.query("SELECT " + COLS + " FROM security_working_time_policies WHERE tenant_id = ? AND effective_from <= ?",
                (rs, i) -> map(rs), tenantId.getValue(), java.sql.Date.valueOf(day));
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> categories(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        jdbc.query("SELECT guard_id, category FROM security_guard_work_category WHERE tenant_id = ?",
                rs -> { m.put((UUID) rs.getObject("guard_id"), rs.getString("category")); }, tenantId.getValue());
        return m;
    }

    @Transactional
    public Policy create(TenantId tenantId, UUID actor, Policy in) {
        Policy p = WorkingTimePolicy.validate(in, za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId));
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO security_working_time_policies (id, tenant_id, category, effective_from, warn_at, ordinary_weekly, max_overtime_weekly,
                        max_daily, max_daily_overtime, min_rest_hours, legal_basis, created_by, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?, now())""",
                    id, tenantId.getValue(), p.category(), java.sql.Date.valueOf(p.effectiveFrom()), p.warnAt(), p.ordinaryWeekly(), p.maxOvertimeWeekly(),
                    p.maxDaily(), p.maxDailyOvertime(), p.minRestHours(), p.legalBasis(), actor);
        } catch (DuplicateKeyException e) {
            throw new HandyFlowException("This category already has a policy starting on that date. Choose a later date", HttpStatus.CONFLICT, "POLICY_EXISTS");
        }
        audit(tenantId.getValue(), actor, "WORKING_TIME_POLICY", id, "POLICY_ADDED",
                "{\"category\":\"" + p.category() + "\",\"effectiveFrom\":\"" + p.effectiveFrom() + "\"}");
        return new Policy(id, p.category(), p.effectiveFrom(), p.warnAt(), p.ordinaryWeekly(), p.maxOvertimeWeekly(), p.maxDaily(), p.maxDailyOvertime(), p.minRestHours(), p.legalBasis());
    }

    /** DEFAULT removes the guard's own category. Any other category must already have a policy in this company, so a typo cannot silently fall back. */
    @Transactional
    public String assign(TenantId tenantId, UUID actor, UUID guardId, String rawCategory) {
        UUID tenant = tenantId.getValue();
        String category = WorkingTimePolicy.category(rawCategory);
        Integer found = jdbc.queryForObject("SELECT count(*) FROM security_guards WHERE id = ? AND tenant_id = ?", Integer.class, guardId, tenant);
        if (found == null || found == 0) throw new HandyFlowException("Guard not found", HttpStatus.NOT_FOUND, "GUARD_NOT_FOUND");
        if (WorkingTimePolicy.DEFAULT_CATEGORY.equals(category)) {
            jdbc.update("DELETE FROM security_guard_work_category WHERE guard_id = ? AND tenant_id = ?", guardId, tenant);
        } else {
            Integer known = jdbc.queryForObject("SELECT count(*) FROM security_working_time_policies WHERE tenant_id = ? AND category = ?", Integer.class, tenant, category);
            if (known == null || known == 0) throw new HandyFlowException("No policy exists for that category yet. Add one first", HttpStatus.CONFLICT, "UNKNOWN_CATEGORY");
            jdbc.update("""
                    INSERT INTO security_guard_work_category (guard_id, tenant_id, category, updated_by, updated_at) VALUES (?,?,?,?, now())
                    ON CONFLICT (guard_id) DO UPDATE SET category = EXCLUDED.category, updated_by = EXCLUDED.updated_by, updated_at = now()
                    WHERE security_guard_work_category.tenant_id = EXCLUDED.tenant_id""", guardId, tenant, category, actor);
        }
        audit(tenant, actor, "GUARD", guardId, "WORK_CATEGORY_SET", "{\"category\":\"" + category + "\"}");
        return category;
    }

    private void audit(UUID tenant, UUID actor, String entityType, UUID entityId, String action, String metadataJson) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', ?, ?, ?, ?::jsonb, now())""", tenant, actor, entityType, entityId, action, metadataJson);
    }
}
