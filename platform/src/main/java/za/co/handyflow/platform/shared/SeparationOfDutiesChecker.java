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

    /**
     * Refuses (409 SAME_PERSON) when {@code actor} repeats {@code earlier}'s step and someone else holding
     * {@code permission} could do it. When nobody else could, allows it and logs a warning naming {@code what}.
     */
    public void require(TenantId tenantId, String permission, UUID earlier, UUID actor, String message, String what) {
        if (earlier == null || actor == null || !earlier.equals(actor)) return;
        long others = otherHolders(tenantId, permission, actor);
        String problem = SeparationOfDuties.problem(message, earlier, actor, others);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "SAME_PERSON");
        if (SeparationOfDuties.soleException(earlier, actor, others)) {
            log.warn("[SOD] Separation of duties not possible, allowed: {} tenant={} user={} (nobody else holds {})",
                    what, tenantId, actor, permission);
        }
    }

    /** Same as {@link #require} for a conflict already decided by the caller (for example "this claim pays you"). */
    public void requireIfConflict(TenantId tenantId, String permission, boolean conflict, UUID actor, String message, String what) {
        if (!conflict) return;
        require(tenantId, permission, actor, actor, message, what);
    }
}
