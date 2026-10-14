package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Applies {@link ApDutyRules} (finance patch F6). Counting who else holds a permission reads the identity tables directly,
 * as AP already does for the ledger; AP has no identity dependency and this is one count.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ApDutyChecks {

    private final JdbcTemplate jdbc;

    /** Active people in the tenant, other than {@code actor}, who hold {@code permission}. */
    long otherHolders(TenantId tenantId, String permission, UUID actor) {
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

    /** Refuses (409 SAME_PERSON) when {@code actor} repeats {@code earlier}'s step and someone else could do it. */
    void require(TenantId tenantId, ApDutyRules.Duty duty, String permission, UUID earlier, UUID actor, String what) {
        if (earlier == null || actor == null || !earlier.equals(actor)) return;
        long others = otherHolders(tenantId, permission, actor);
        String problem = ApDutyRules.problem(duty, earlier, actor, others);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "SAME_PERSON");
        if (ApDutyRules.soleException(earlier, actor, others)) {
            log.warn("[AP] Separation of duties not possible, allowed: duty={} {} tenant={} user={} (nobody else holds {})",
                    duty, what, tenantId, actor, permission);
        }
    }
}
