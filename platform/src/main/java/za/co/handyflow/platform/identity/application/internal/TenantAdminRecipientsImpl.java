package za.co.handyflow.platform.identity.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link TenantAdminRecipients}: resolves the tenant's active,
 * non-deleted users as admin recipients via a native query — same pattern
 * SubscriptionController.fetchTenantDetails() and PmNotificationService
 * .findAdminEmail() already used independently. This is the one place that
 * logic should live.
 *
 * WHY LIMIT 5? Tenant-wide compliance/operational alerts (SARS deadlines,
 * PSiRA expiry, low stock) are meant for whoever manages the account, not
 * every seat-holder. If/when a proper role table exists, replace the
 * ORDER BY created_at LIMIT 5 heuristic with "WHERE role = 'ADMIN'" or
 * equivalent — this implementation is intentionally the simplest thing that
 * fixes the silently-dropped alerts today, not the final word on admin
 * targeting.
 *
 * Placed in the Identity module because it is the only module with a
 * legitimate reason to query users/tenants directly — every other module
 * depends on this interface, never on Identity's internals.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAdminRecipientsImpl implements TenantAdminRecipients {

    private final JdbcTemplate jdbc;

    @Override
    public List<Recipient> resolveTenantAdmins(TenantId tenantId) {
        try {
            return jdbc.query("""
                    SELECT u.id AS user_id, u.email, u.first_name, u.last_name, u.phone
                    FROM tenants t
                    JOIN users u ON u.tenant_id = t.id
                    WHERE t.id = ? AND u.deleted_at IS NULL AND u.email IS NOT NULL
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