package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteAccessRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the caller's {@link AccessScope}. Authority checks (what a user may DO) stay on the
 * controllers; this class answers WHICH records the user may do it to.
 * <ol>
 *   <li>FACILITIES_ADMIN — unrestricted, always.</li>
 *   <li>Field role: FACILITIES_TECHNICIAN without FACILITIES_MANAGE/READ — only jobs assigned to the
 *       technician record linked to this user, and the sites those jobs are at.</li>
 *   <li>Requester-only: FACILITIES_WORKORDER_REQUEST without MANAGE/READ or any operational authority
 *       (technician, create, assign, approve, verify) — only the requests they raised.</li>
 *   <li>Otherwise — site-access rows restrict to those sites; no rows means unrestricted.</li>
 * </ol>
 * Holding a broader authority alongside a narrower one means the broader access wins. Tenant isolation
 * is unchanged and still enforced by every repository query.
 */
@Component
@RequiredArgsConstructor
public class FacilityAccessPolicy {

    private static final Set<String> OPERATIONAL = Set.of("FACILITIES_TECHNICIAN", "FACILITIES_WORKORDER_CREATE",
            "FACILITIES_WORKORDER_ASSIGN", "FACILITIES_WORKORDER_APPROVE", "FACILITIES_WORKORDER_VERIFY");

    private final FacilitySiteAccessRepository siteAccessRepository;
    private final FacilityTechnicianRepository technicianRepository;
    private final FacilityWorkOrderRepository workOrderRepository;

    public AccessScope scope(TenantId tenantId) {
        Set<String> authorities = currentAuthorities();
        if (authorities.contains("FACILITIES_ADMIN")) {
            return AccessScope.unrestricted();
        }

        UUID userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            throw new HandyFlowException("Not authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }

        boolean fullView = authorities.contains("FACILITIES_MANAGE") || authorities.contains("FACILITIES_READ");
        if (authorities.contains("FACILITIES_TECHNICIAN") && !fullView) {
            List<UUID> technicianIds = technicianRepository.findIdsByLinkedUser(tenantId, userId);
            List<UUID> siteIds = technicianIds.isEmpty()
                    ? List.of() : workOrderRepository.findSiteIdsForTechnicians(tenantId, technicianIds);
            return AccessScope.assignedJobs(siteIds, technicianIds);
        }

        List<UUID> allowedSites = siteAccessRepository.findSiteIds(tenantId, userId);

        boolean operational = authorities.stream().anyMatch(OPERATIONAL::contains);
        if (authorities.contains("FACILITIES_WORKORDER_REQUEST") && !fullView && !operational) {
            return AccessScope.forRequester(allowedSites, userId);
        }

        return allowedSites.isEmpty() ? AccessScope.unrestricted() : AccessScope.restrictedToSites(allowedSites);
    }

    /** Whether the current caller holds an authority — for in-service decisions such as audited admin overrides. */
    public boolean hasAuthority(String authority) {
        return currentAuthorities().contains(authority);
    }

    private Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}