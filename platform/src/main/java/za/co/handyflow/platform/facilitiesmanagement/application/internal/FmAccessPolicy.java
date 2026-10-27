package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientAccessRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmTechnicianRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the caller's {@link FmAccessScope} for STAFF requests. Authority checks (what a user may DO)
 * stay on the controllers; this class answers WHICH records the user may do it to. The client portal has
 * its own boundary ({@code FmPortalDataService}) and does not use this.
 * <ol>
 *   <li>FACILITIESMANAGEMENT_ADMIN — unrestricted, always.</li>
 *   <li>Field role: FACILITIESMANAGEMENT_TECHNICIAN without MANAGE/READ — only jobs assigned to the
 *       technician record linked to this user, and the sites those jobs are at.</li>
 *   <li>Otherwise — client-access rows restrict to those clients; no rows means unrestricted.</li>
 * </ol>
 * Holding MANAGE or READ alongside TECHNICIAN means the broader access wins. Tenant isolation is
 * unchanged and still enforced by every repository query.
 */
@Component
@RequiredArgsConstructor
public class FmAccessPolicy {

    private final FmClientAccessRepository clientAccessRepository;
    private final FmTechnicianRepository technicianRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmSiteRepository siteRepository;

    public FmAccessScope scope(TenantId tenantId) {
        Set<String> authorities = currentAuthorities();
        if (authorities.contains("FACILITIESMANAGEMENT_ADMIN")) {
            return FmAccessScope.unrestrictedScope();
        }

        UUID userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            throw new HandyFlowException("Not authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }

        boolean fullView = authorities.contains("FACILITIESMANAGEMENT_MANAGE") || authorities.contains("FACILITIESMANAGEMENT_READ");
        if (authorities.contains("FACILITIESMANAGEMENT_TECHNICIAN") && !fullView) {
            List<UUID> technicianIds = technicianRepository.findIdsByLinkedUser(tenantId, userId);
            List<UUID> siteIds = technicianIds.isEmpty()
                    ? List.of() : workOrderRepository.findSiteIdsForTechnicians(tenantId, technicianIds);
            return FmAccessScope.assignedJobs(siteIds, technicianIds);
        }

        List<UUID> clientIds = clientAccessRepository.findClientIds(tenantId, userId);
        if (clientIds.isEmpty()) {
            return FmAccessScope.unrestrictedScope();
        }
        return FmAccessScope.restrictedToClients(clientIds, siteRepository.findSiteIdsForClients(tenantId, clientIds));
    }

    /** Whether the current caller holds an authority — for in-service decisions. */
    public boolean hasAuthority(String authority) {
        return currentAuthorities().contains(authority);
    }

    private Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}