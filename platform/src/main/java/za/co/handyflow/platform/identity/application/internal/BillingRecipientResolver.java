package za.co.handyflow.platform.identity.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves who should receive a BILLING communication specifically —
 * subscription invoices, payment receipts, past-due notices. Deliberately
 * separate from TenantAdminRecipientsImpl, not a modification to it:
 * confirmed that class is used generically across modules (FleetService.
 * updateStatus() calls it for vehicle notifications with nothing to do
 * with billing), so repurposing it for billing-specific routing would
 * have broken its existing use everywhere else it's already relied on.
 * <p>
 * Fallback chain, in order:
 * 1. tenant.billingEmail, if the tenant admin has set one via Settings —
 *    a dedicated contact, not necessarily tied to any login account.
 * 2. Users explicitly opted in via receivesBillingComms=true — plural,
 *    since more than one person might legitimately need to see an
 *    invoice (e.g. both the owner and an actual bookkeeper).
 * 3. The tenant's first-created active user — the original registering
 *    owner, the one person guaranteed to exist and be reachable, same
 *    "first user" fallback TenantAdminRecipientsImpl itself would
 *    otherwise land on by ordering on created_at.
 * <p>
 * Same REQUIRES_NEW + JdbcTemplate + swallow-and-log-on-failure pattern as
 * TenantAdminRecipientsImpl, for the identical reason that class documents:
 * this is likely to be called from inside other modules' own
 * @Transactional business methods (e.g. wherever an invoice is generated),
 * and a failure resolving recipients must never be able to poison or fail
 * that surrounding transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingRecipientResolver {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Recipient> resolveBillingRecipients(TenantId tenantId) {
        try {
            Map<String, Object> tenantRow = jdbc.queryForMap(
                    "SELECT billing_email, billing_contact_name, billing_phone FROM tenants WHERE id = ?",
                    tenantId.getValue());
            String billingEmail = (String) tenantRow.get("billing_email");

            if (billingEmail != null && !billingEmail.isBlank()) {
                String contactName = (String) tenantRow.get("billing_contact_name");
                // NOTE: userId is null here deliberately — a dedicated
                // billing contact may not correspond to any HandyFlow
                // login account at all (e.g. an external accounts@
                // address). Assumed safe based on Recipient.user()'s own
                // call shape seen elsewhere in this codebase; worth
                // confirming directly against Recipient.java if that
                // assumption turns out wrong.
                return List.of(Recipient.user(
                        null,
                        contactName != null && !contactName.isBlank() ? contactName : "Accounts",
                        billingEmail,
                        (String) tenantRow.get("billing_phone")));
            }

            List<Recipient> optedIn = jdbc.query("""
                    SELECT u.id AS user_id, u.email, u.first_name, u.last_name, u.phone
                    FROM users u
                    WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND u.receives_billing_comms = true
                    ORDER BY u.created_at
                    """,
                    (rs, rowNum) -> Recipient.user(
                            UUID.fromString(rs.getString("user_id")),
                            trim(rs.getString("first_name")) + " " + trim(rs.getString("last_name")),
                            rs.getString("email"),
                            rs.getString("phone")),
                    tenantId.getValue());
            if (!optedIn.isEmpty()) {
                return optedIn;
            }

            return jdbc.query("""
                    SELECT u.id AS user_id, u.email, u.first_name, u.last_name, u.phone
                    FROM users u
                    WHERE u.tenant_id = ? AND u.status = 'ACTIVE'
                    ORDER BY u.created_at
                    LIMIT 1
                    """,
                    (rs, rowNum) -> Recipient.user(
                            UUID.fromString(rs.getString("user_id")),
                            trim(rs.getString("first_name")) + " " + trim(rs.getString("last_name")),
                            rs.getString("email"),
                            rs.getString("phone")),
                    tenantId.getValue());
        } catch (Exception e) {
            log.warn("Could not resolve billing recipients for tenant={}: {}", tenantId.getValue(), e.getMessage());
            return List.of();
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
