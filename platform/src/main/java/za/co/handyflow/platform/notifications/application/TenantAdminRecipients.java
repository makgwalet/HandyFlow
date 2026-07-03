package za.co.handyflow.platform.notifications.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * TenantAdminRecipients — resolves who should receive tenant-wide operational
 * and compliance notifications (SARS deadlines, PSiRA/firearm-license expiry,
 * no-show alerts, low-stock digests, PO approvals, etc.) when the event has
 * no single "obvious" recipient the way a booking or a shift does.
 *
 * WHY THIS EXISTS
 * Before this, at least six places independently reinvented "look up the
 * tenant's admin email": PmNotificationService.findAdminEmail(),
 * ScmNotificationService.findAdminEmail(), PsiraComplianceScheduler
 * .resolveAdminEmail() (TODO, returns null), NoShowAlertScheduler
 * .resolveAdminEmail() (TODO, returns null), AccountantScheduler
 * .lookupFirmEmail(), and SubscriptionController.fetchTenantDetails().
 * Two of those (Psira, NoShow) never got wired up — their alerts have been
 * silently dropped in production. This interface gives every module one
 * correct, tested implementation instead of N half-finished ones.
 *
 * WHY RETURN Recipient AND NOT JUST String email?
 * A Recipient carries userId, which is what makes the notification show up
 * in that admin's in-app bell (NotificationService only writes an IN_APP row
 * for recipients where isPlatformUser() is true). An email-only lookup can
 * never produce an in-app notification — which is exactly the gap this
 * change is meant to close for the compliance schedulers.
 *
 * Implemented once, in the module that actually owns tenant/user data
 * (Identity), and depended on by every module that needs to notify "the
 * tenant" rather than a specific business-entity owner — same dependency
 * direction as FleetNotificationRecipients in earthmoving.
 */
public interface TenantAdminRecipients {

    /**
     * Users who should receive tenant-wide operational/compliance
     * notifications for this tenant. Typically the tenant's admin/owner
     * users. Returns an empty list (never throws) if none can be resolved,
     * so callers can safely no-op rather than crash a scheduled job.
     */
    List<za.co.handyflow.platform.notifications.application.Recipient> resolveTenantAdmins(TenantId tenantId);

    /** Convenience overload for schedulers that only have a raw tenant UUID. */
    default List<za.co.handyflow.platform.notifications.application.Recipient> resolveTenantAdmins(UUID tenantId) {
        return resolveTenantAdmins(TenantId.of(tenantId));
    }
}