package za.co.handyflow.platform.earthmoving.application.port;

import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * *** INTEGRATION POINT — READ BEFORE USING ***
 * <p>
 * When a machine breaks down, or a service is due, or a hire period is
 * ending — WHO gets notified? That's a question about roles and users,
 * which live in your Identity/Security module, not in earthmoving. Rather
 * than have EarthAssetService reach directly into that module (a dependency
 * pointing the wrong way — a niche operational module depending on your
 * core identity system, and dragging that dependency into every module that
 * ever wants to notify someone), earthmoving depends on this small port
 * interface instead.
 * <p>
 * TO WIRE THIS UP: implement this interface somewhere that DOES have access
 * to users/roles (e.g. in your Identity or Security module) — something
 * like "find all users in this tenant with the FLEET_MANAGER role/permission,
 * return their id/email/phone as Recipients" — and expose it as a
 * {@code @Component}/{@code @Service} bean. Spring will wire it in here
 * automatically since it's just an interface injection.
 * <p>
 * Until that's wired, {@link NoOpFleetNotificationRecipients} is the active
 * implementation: it logs a warning and returns no recipients, so the app
 * runs correctly (nobody gets notified, nothing throws) rather than failing
 * to start with a missing-bean error.
 */
public interface FleetNotificationRecipients {

    /**
     * Users who should be notified about equipment lifecycle events
     * (breakdowns, service due, hire period ending) for this tenant.
     */
    List<Recipient> resolveFleetManagers(TenantId tenantId);
}