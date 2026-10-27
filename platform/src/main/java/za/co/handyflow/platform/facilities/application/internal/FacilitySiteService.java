package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.SiteResponse;
import za.co.handyflow.platform.facilities.dto.UpsertSiteRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 3: every read and write is filtered through the caller's
 * {@link AccessScope}; closing, reopening and deleting a site are audited;
 * a site-restricted user cannot create new sites (they would immediately
 * fall outside their own scope).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySiteService {

    private final FacilitySiteRepository siteRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<SiteResponse> getSites(TenantId tenantId, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        return siteRepository.findAllActiveInScope(tenantId, scope.allSites(), scope.siteIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SiteResponse getSite(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    @Transactional
    public SiteResponse createSite(TenantId tenantId, UpsertSiteRequest req) {
        if (!accessPolicy.scope(tenantId).allSites()) {
            throw new HandyFlowException("Site-restricted users cannot create new sites",
                    HttpStatus.FORBIDDEN, "SITE_SCOPE_RESTRICTED");
        }
        FacilitySite site = FacilitySite.create(tenantId, req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        log.info("Facility site created id={} tenant={}", site.getId(), tenantId);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse updateSite(TenantId tenantId, UUID id, UpsertSiteRequest req) {
        FacilitySite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.update(req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse closeSite(TenantId tenantId, UUID id) {
        FacilitySite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.close();
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_CLOSED", "SITE", id, "name=" + site.getName());
        return toResponse(site);
    }

    @Transactional
    public SiteResponse reopenSite(TenantId tenantId, UUID id) {
        FacilitySite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.reopen();
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_REOPENED", "SITE", id, "name=" + site.getName());
        return toResponse(site);
    }

    @Transactional
    public void deleteSite(TenantId tenantId, UUID id, UUID deletedByUserId) {
        FacilitySite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.softDelete(deletedByUserId);
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_DELETED", "SITE", id, "name=" + site.getName());
    }

    private FacilitySite findActive(TenantId tenantId, UUID id, AccessScope scope) {
        FacilitySite site = siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", id.toString()));
        scope.requireSite(site.getId());
        return site;
    }

    private SiteResponse toResponse(FacilitySite s) {
        return new SiteResponse(s.getId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getNotes(), s.getStatus(), s.getCreatedAt());
    }
}