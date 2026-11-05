package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Applies {@link SeparationOfDuties}. Counting who else holds a permission reads the identity tables directly, as AP's
 * own duty checks do, so that modules using this need no dependency on identity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeparationOfDutiesChecker {

    private final JdbcTemplate jdbc;

    /** Active people in the tenant, other than {@code actor}, who hold {@code permission}. */
    public long otherHolders(TenantId tenantId, String permission, UUID actor) {
        Long n = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id)
                  FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ? AND u.id <> ?""",
                Long.class, tenantId.getValue(), permission, actor);
        return n != null ? n : 0;
    }

    /** Audit-trail action written when a step is allowed only because nobody else could do it. */
    public static final String SOLE_APPROVER_ACTION = "SOLE_APPROVER_SELF_APPROVAL";

    /**
     * Refuses (409 SAME_PERSON) when {@code actor} repeats {@code earlier}'s step and someone else holding
     * {@code permission} could do it. When nobody else could, allows it, logs a warning and writes an audit event
     * (action {@value #SOLE_APPROVER_ACTION}) the owner can hand to an accountant or auditor.
     */
    public void require(TenantId tenantId, String permission, UUID earlier, UUID actor, String message,
                        String itemType, UUID itemId, String what) {
        if (earlier == null || actor == null || !earlier.equals(actor)) return;
        long others = otherHolders(tenantId, permission, actor);
        String problem = SeparationOfDuties.problem(message, earlier, actor, others);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "SAME_PERSON");
        if (SeparationOfDuties.soleException(earlier, actor, others)) recordSoleException(tenantId, actor, permission, itemType, itemId, what);
    }

    /** Same as {@link #require} for a conflict already decided by the caller (for example "this claim pays you"). */
    public void requireIfConflict(TenantId tenantId, String permission, boolean conflict, UUID actor, String message,
                                  String itemType, UUID itemId, String what) {
        if (!conflict) return;
        require(tenantId, permission, actor, actor, message, itemType, itemId, what);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    /**
     * As above, also keeping {@code details} (who asked, the reason, when, before and after values) in the audit event,
     * so the Owner's report can show what the one person approved and why.
     */
    public void recordSoleException(TenantId tenantId, UUID actor, String permission, String itemType, UUID itemId, String what,
                                    java.util.Map<String, ?> details) {
        log.warn("[SOD] Separation of duties not possible, allowed: {} tenant={} user={} (nobody else holds {})",
                what, tenantId, actor, permission);
        String name = null;
        try { name = TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user in context */ }
        String extra;
        try { extra = JSON.writeValueAsString(details == null ? java.util.Map.of() : details); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { extra = "{}"; }
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json)
                VALUES (?, 'STAFF', ?, ?, ?, ?, ?, jsonb_build_object('permission', ?::text, 'detail', ?::text) || ?::jsonb)""",
                tenantId.getValue(), actor, name, SOLE_APPROVER_ACTION, itemType, itemId, permission, what, extra);
    }

    /** Logs and records (append-only audit event) that {@code actor} did a step nobody else could check. */
    public void recordSoleException(TenantId tenantId, UUID actor, String permission, String itemType, UUID itemId, String what) {
        log.warn("[SOD] Separation of duties not possible, allowed: {} tenant={} user={} (nobody else holds {})",
                what, tenantId, actor, permission);
        String name = null;
        try { name = TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user in context */ }
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json)
                VALUES (?, 'STAFF', ?, ?, ?, ?, ?, jsonb_build_object('permission', ?::text, 'detail', ?::text))""",
                tenantId.getValue(), actor, name, SOLE_APPROVER_ACTION, itemType, itemId, permission, what);
    }
}
