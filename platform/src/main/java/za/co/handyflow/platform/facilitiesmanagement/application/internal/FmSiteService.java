package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmSiteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSiteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmSiteRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 5: reads and writes are limited to the caller's {@link FmAccessScope}; a site is visible when it
 * belongs to one of the user's clients (or, for field technicians, hosts one of their assigned jobs).
 * Closing, reopening and deleting a site are audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmSiteService {

    private final FmSiteRepository siteRepository;
    private final FmClientRepository clientRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public Page<FmSiteResponse> getSites(TenantId tenantId, UUID clientId, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        if (clientId != null) {
            scope.requireClient(clientId);
            return siteRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
        }
        return siteRepository.findAllActiveInScope(tenantId, scope.unrestricted(), scope.siteIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmSiteResponse getSite(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    @Transactional
    public FmSiteResponse createSite(TenantId tenantId, CreateFmSiteRequest req) {
        accessPolicy.scope(tenantId).requireClient(req.clientId());
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));

        FmSite site = FmSite.create(tenantId, req.clientId(), req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        log.info("FM site created id={} client={} tenant={}", site.getId(), req.clientId(), tenantId);
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse updateSite(TenantId tenantId, UUID id, UpdateFmSiteRequest req) {
        FmSite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.update(req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse closeSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.close();
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_CLOSED", "SITE", id, "name=" + site.getName() + " client=" + site.getClientId());
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse reopenSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.reopen();
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_REOPENED", "SITE", id, "name=" + site.getName() + " client=" + site.getClientId());
        return toResponse(site);
    }

    @Transactional
    public void deleteSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id, accessPolicy.scope(tenantId));
        site.softDelete();
        siteRepository.save(site);
        auditService.record(tenantId, "SITE_DELETED", "SITE", id, "name=" + site.getName() + " client=" + site.getClientId());
    }

    private FmSite findActive(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmSite site = siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", id.toString()));
        if (!scope.canSeeSite(site.getId())) {
            throw new ResourceNotFoundException("FmSite", id.toString());
        }
        return site;
    }

    private FmSiteResponse toResponse(FmSite s) {
        return new FmSiteResponse(s.getId(), s.getClientId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getNotes(), s.getStatus(), s.getCreatedAt());
    }
}