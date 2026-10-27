package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * What the current staff caller may see, resolved once per request by {@link FmAccessPolicy}.
 * <ul>
 *   <li>{@code unrestricted} — no client/site restriction.</li>
 *   <li>otherwise {@code clientIds} limits client-keyed records (clients, agreements, invoices, work
 *       orders, portal access) and {@code siteIds} limits site-keyed ones (sites, assets, PPM).
 *       For a client-restricted user, {@code siteIds} are the sites of their clients.</li>
 *   <li>{@code assignedOnly} — a field technician: only work orders assigned to {@code technicianIds},
 *       and only the sites those jobs are at. No access to client, agreement or billing records.</li>
 * </ul>
 * Id lists are never empty (placeholder) so they bind straight into JPQL {@code IN} clauses; they're only
 * consulted when the matching flag restricts access. Out-of-scope records are reported as NOT FOUND, not
 * FORBIDDEN, so a restricted user can't probe which ids exist.
 */
public record FmAccessScope(boolean unrestricted, List<UUID> clientIds, List<UUID> siteIds,
                            boolean assignedOnly, List<UUID> technicianIds) {

    private static final UUID PLACEHOLDER = new UUID(0L, 0L);

    public static FmAccessScope unrestrictedScope() {
        return new FmAccessScope(true, List.of(PLACEHOLDER), List.of(PLACEHOLDER), false, List.of(PLACEHOLDER));
    }

    public static FmAccessScope restrictedToClients(List<UUID> clientIds, List<UUID> siteIdsOfThoseClients) {
        return new FmAccessScope(false, nonEmpty(clientIds), nonEmpty(siteIdsOfThoseClients), false, List.of(PLACEHOLDER));
    }

    public static FmAccessScope assignedJobs(List<UUID> siteIdsOfJobs, List<UUID> technicianIds) {
        return new FmAccessScope(false, List.of(PLACEHOLDER), nonEmpty(siteIdsOfJobs), true, nonEmpty(technicianIds));
    }

    private static List<UUID> nonEmpty(List<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of(PLACEHOLDER) : ids;
    }

    public boolean allJobs() { return !assignedOnly; }

    /** For work-order queries: the client filter is skipped for unrestricted users and for technicians (who are filtered by assignment instead). */
    public boolean workOrderClientFilterOff() { return unrestricted || assignedOnly; }

    public boolean canSeeClient(UUID clientId) { return unrestricted || clientIds.contains(clientId); }

    public boolean canSeeSite(UUID siteId) { return unrestricted || siteIds.contains(siteId); }

    public boolean canSeeWorkOrder(FmWorkOrder wo) {
        if (assignedOnly) return wo.getTechnicianId() != null && technicianIds.contains(wo.getTechnicianId());
        return canSeeClient(wo.getClientId());
    }

    public void requireClient(UUID clientId) {
        if (!canSeeClient(clientId)) throw new ResourceNotFoundException("FmClient", clientId.toString());
    }

    public void requireSite(UUID siteId) {
        if (!canSeeSite(siteId)) throw new ResourceNotFoundException("FmSite", siteId.toString());
    }
}