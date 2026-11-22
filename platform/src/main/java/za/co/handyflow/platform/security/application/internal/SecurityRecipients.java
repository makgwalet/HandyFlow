package za.co.handyflow.platform.security.application.internal;

import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Who hears about Security events (no-shows, gate overstays, duress and critical alarms, compliance expiries,
 * phone problems): the active users whose role holds {@value #PERMISSION}. A tenant that has nobody with that
 * permission falls back to the tenant admins, as before, so nothing goes silent.
 */
public final class SecurityRecipients {

    public static final String PERMISSION = "SECURITY_ALERTS_RECEIVE";
    public static final int LIMIT = 25;

    private SecurityRecipients() {}

    public static List<Recipient> forAlerts(TenantAdminRecipients source, TenantId tenantId) {
        List<Recipient> holders;
        try {
            holders = source.resolveByPermission(tenantId, PERMISSION, LIMIT);
        } catch (RuntimeException e) {
            holders = List.of();   // a lookup problem must not silence the alert: use the fallback
        }
        if (holders != null && !holders.isEmpty()) return holders;
        return source.resolveTenantAdmins(tenantId);
    }

    public static List<Recipient> forAlerts(TenantAdminRecipients source, UUID tenantId) {
        return forAlerts(source, TenantId.of(tenantId));
    }
}
