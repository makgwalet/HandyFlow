package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * What the current caller may see, resolved once per request by {@link FacilityAccessPolicy}.
 * <ul>
 *   <li>{@code allSites} — true: no site restriction. False: only {@code siteIds}.</li>
 *   <li>{@code assignedOnly} — true: only work orders assigned to {@code technicianIds} (field technician).</li>
 *   <li>{@code requesterOnly} — true: only work orders requested by {@code requesterUserId} (people who can
 *       raise requests but not see the wider job list).</li>
 * </ul>
 * The id lists and requester id are never empty/null (placeholders) so they bind straight into JPQL;
 * they are only consulted when the matching flag restricts access. Out-of-scope records are reported
 * as NOT FOUND, not FORBIDDEN, so a restricted user can't probe which ids exist.
 */
public record AccessScope(boolean allSites, List<UUID> siteIds, boolean assignedOnly, List<UUID> technicianIds,
                          boolean requesterOnly, UUID requesterUserId) {

    private static final UUID PLACEHOLDER = new UUID(0L, 0L);

    public static AccessScope unrestricted() {
        return new AccessScope(true, List.of(PLACEHOLDER), false, List.of(PLACEHOLDER), false, PLACEHOLDER);
    }

    public static AccessScope restrictedToSites(List<UUID> siteIds) {
        return new AccessScope(false, nonEmpty(siteIds), false, List.of(PLACEHOLDER), false, PLACEHOLDER);
    }

    public static AccessScope assignedJobs(List<UUID> siteIdsOfJobs, List<UUID> technicianIds) {
        return new AccessScope(false, nonEmpty(siteIdsOfJobs), true, nonEmpty(technicianIds), false, PLACEHOLDER);
    }

    /** Own requests only; sites limited to {@code allowedSiteIds} when the user has site rows, otherwise any site. */
    public static AccessScope forRequester(List<UUID> allowedSiteIds, UUID userId) {
        boolean anySite = allowedSiteIds == null || allowedSiteIds.isEmpty();
        return new AccessScope(anySite, nonEmpty(allowedSiteIds), false, List.of(PLACEHOLDER), true, userId);
    }

    private static List<UUID> nonEmpty(List<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of(PLACEHOLDER) : ids;
    }

    /** True when jobs are not narrowed to the caller's own assignments. */
    public boolean allJobs() { return !assignedOnly; }

    /** True when requests are not narrowed to the ones the caller raised. */
    public boolean allRequesters() { return !requesterOnly; }

    public boolean canSeeSite(UUID siteId) { return allSites || siteIds.contains(siteId); }

    public boolean canSeeWorkOrder(FacilityWorkOrder wo) {
        if (!canSeeSite(wo.getSiteId())) return false;
        if (assignedOnly && (wo.getTechnicianId() == null || !technicianIds.contains(wo.getTechnicianId()))) return false;
        return !requesterOnly || requesterUserId.equals(wo.getRequestedByUserId());
    }

    public void requireSite(UUID siteId) {
        if (!canSeeSite(siteId)) throw new ResourceNotFoundException("FacilitySite", siteId.toString());
    }
}