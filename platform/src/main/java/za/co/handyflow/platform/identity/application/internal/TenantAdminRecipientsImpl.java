package za.co.handyflow.platform.identity.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link TenantAdminRecipients}: resolves the tenant's active users
 * as admin recipients via a native query.
 * <p>
 * *** TWO FIXES HERE — read both before touching this file again ***
 * <p>
 * 1. FIX (schema bug): the query used to filter on {@code u.deleted_at IS
 * NULL} — that column doesn't exist on {@code users}. Users are deactivated
 * via a {@code status} column (see {@code User.activate()}/{@code
 * deactivate()} and {@code UserResponse.status()} in the identity module),
 * not soft-deleted. Fixed to {@code u.status = 'ACTIVE'} — if your actual
 * status enum uses different string values, adjust this literal to match
 * (check the values {@code User}'s status enum actually persists as).
 * <p>
 * 2. FIX (transaction-poisoning bug, more important than #1): this class is
 * called synchronously from inside other modules' {@code @Transactional}
 * business methods (e.g. {@code FleetService.updateStatus()} while it's
 * mid-transaction updating a vehicle). Before this fix, that meant a failure
 * in THIS query — even though caught by the try/catch below — still left
 * the surrounding database transaction in Postgres's "aborted, commands
 * ignored until rollback" state, because catching a Java exception does not
 * un-poison a database connection once Postgres has marked its current
 * transaction failed. The caller's otherwise-successful business update
 * (the vehicle status change) then failed to commit — a notification
 * lookup problem took down an unrelated business operation, which should
 * never be possible.
 * <p>
 * {@code @Transactional(propagation = REQUIRES_NEW)} forces this method
 * onto its own separate transaction (and connection) regardless of what
 * transaction is already open in the calling code. If this query fails now,
 * only this isolated mini-transaction is affected — the caller's
 * transaction is suspended untouched while this runs, and resumes normally
 * once this method returns. This is the general fix: it protects against
 * ANY future failure in this class, not just the specific column bug above.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAdminRecipientsImpl implements TenantAdminRecipients {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Recipient> resolveTenantAdmins(TenantId tenantId) {
        try {
            return jdbc.query("""
                    SELECT u.id AS user_id, u.email, u.first_name, u.last_name, u.phone
                    FROM tenants t
                    JOIN users u ON u.tenant_id = t.id
                    WHERE t.id = ? AND u.status = 'ACTIVE' AND u.email IS NOT NULL
                    ORDER BY u.created_at
                    LIMIT 5
                    """,
                    (rs, rowNum) -> Recipient.user(
                            UUID.fromString(rs.getString("user_id")),
                            trim(rs.getString("first_name")) + " " + trim(rs.getString("last_name")),
                            rs.getString("email"),
                            rs.getString("phone")),
                    tenantId.getValue());
        } catch (Exception e) {
            log.warn("Could not resolve tenant admin recipients for tenant={}: {}",
                    tenantId.getValue(), e.getMessage());
            return List.of();
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}