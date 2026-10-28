package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterApprovalRules;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Who may approve a job or an offer: the company's active users whose roles carry RECRUITER_APPROVE. Approval requests and reminders go
 * to them, and only to them, instead of to every administrator. The person who asked is never one of them.
 */
@Component
@RequiredArgsConstructor
class RecruiterApprovers {

    static final String PERMISSION = "RECRUITER_APPROVE";

    private final JdbcTemplate jdbc;

    /** Every active user in the company who holds the approve permission. */
    List<Recipient> holders(TenantId tenant) {
        return jdbc.query("""
                SELECT DISTINCT u.id, u.first_name || ' ' || u.last_name AS name, u.email
                  FROM users u JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ?
                 ORDER BY name""",
                (rs, n) -> Recipient.user(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("email"), null),
                tenant.getValue(), PERMISSION);
    }

    /** The people to ask about something this user sent. */
    List<Recipient> approversFor(TenantId tenant, UUID submitter) {
        return RecruiterApprovalRules.approversFor(holders(tenant), Recipient::userId, submitter);
    }

    boolean anyoneBut(TenantId tenant, UUID submitter) { return !approversFor(tenant, submitter).isEmpty(); }

    /** Whether this user holds the approve permission in this company. */
    boolean mayApprove(TenantId tenant, UUID userId) {
        return holders(tenant).stream().anyMatch(r -> userId.equals(r.userId()));
    }
}
