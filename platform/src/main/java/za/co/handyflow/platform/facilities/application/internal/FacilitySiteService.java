package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.SiteResponse;
import za.co.handyflow.platform.facilities.dto.UpsertSiteRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySiteService {

    private final FacilitySiteRepository siteRepository;

    @Transactional(readOnly = true)
    public Page<SiteResponse> getSites(TenantId tenantId, Pageable pageable) {
        return siteRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SiteResponse getSite(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public SiteResponse createSite(TenantId tenantId, UpsertSiteRequest req) {
        FacilitySite site = FacilitySite.create(tenantId, req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        log.info("Facility site created id={} tenant={}", site.getId(), tenantId);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse updateSite(TenantId tenantId, UUID id, UpsertSiteRequest req) {
        FacilitySite site = findActive(tenantId, id);
        site.update(req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse closeSite(TenantId tenantId, UUID id) {
        FacilitySite site = findActive(tenantId, id);
        site.close();
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse reopenSite(TenantId tenantId, UUID id) {
        FacilitySite site = findActive(tenantId, id);
        site.reopen();
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public void deleteSite(TenantId tenantId, UUID id, UUID deletedByUserId) {
        FacilitySite site = findActive(tenantId, id);
        site.softDelete(deletedByUserId);
        siteRepository.save(site);
    }

    private FacilitySite findActive(TenantId tenantId, UUID id) {
        return siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", id.toString()));
    }

    private SiteResponse toResponse(FacilitySite s) {
        return new SiteResponse(s.getId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getNotes(), s.getStatus(), s.getCreatedAt());
    }
}
