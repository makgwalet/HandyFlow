package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilitySiteAccess;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteAccessRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.SiteAccessResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manage which sites a user is restricted to. An empty set removes the
 * restriction (the user becomes unrestricted again). {@code userId} is not
 * validated against the identity module (no dependency declared) — callers
 * are ADMIN-only. Every change is audited with the before and after sets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityAccessAdminService {

    private final FacilitySiteAccessRepository siteAccessRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public SiteAccessResponse getSiteAccess(TenantId tenantId, UUID userId) {
        return toResponse(userId, siteAccessRepository.findSiteIds(tenantId, userId));
    }

    @Transactional
    public SiteAccessResponse setSiteAccess(TenantId tenantId, UUID userId, Set<UUID> siteIds) {
        for (UUID siteId : siteIds) {
            siteRepository.findActiveById(tenantId, siteId)
                    .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", siteId.toString()));
        }
        List<UUID> before = siteAccessRepository.findSiteIds(tenantId, userId);

        siteAccessRepository.deleteForUser(tenantId, userId);
        UUID grantedBy = TenantContext.getCurrentUserId();
        for (UUID siteId : siteIds) {
            siteAccessRepository.save(FacilitySiteAccess.grant(tenantId, userId, siteId, grantedBy));
        }

        auditService.record(tenantId, "SITE_ACCESS_CHANGED", "USER", userId,
                "before=" + sorted(before) + " after=" + sorted(List.copyOf(siteIds)));
        log.info("Facilities site access for user={} tenant={} set to {} site(s)", userId, tenantId, siteIds.size());
        return toResponse(userId, List.copyOf(siteIds));
    }

    private SiteAccessResponse toResponse(UUID userId, List<UUID> siteIds) {
        return new SiteAccessResponse(userId, !siteIds.isEmpty(), sorted(siteIds));
    }

    private List<UUID> sorted(List<UUID> ids) {
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }
}