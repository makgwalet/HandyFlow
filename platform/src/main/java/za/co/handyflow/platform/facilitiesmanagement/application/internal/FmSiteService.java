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

@Slf4j
@Service
@RequiredArgsConstructor
public class FmSiteService {

    private final FmSiteRepository siteRepository;
    private final FmClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Page<FmSiteResponse> getSites(TenantId tenantId, UUID clientId, Pageable pageable) {
        if (clientId != null) return siteRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
        return siteRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmSiteResponse getSite(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public FmSiteResponse createSite(TenantId tenantId, CreateFmSiteRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));

        FmSite site = FmSite.create(tenantId, req.clientId(), req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        log.info("FM site created id={} client={} tenant={}", site.getId(), req.clientId(), tenantId);
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse updateSite(TenantId tenantId, UUID id, UpdateFmSiteRequest req) {
        FmSite site = findActive(tenantId, id);
        site.update(req.name(), req.siteType(), req.address(), req.notes());
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse closeSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id);
        site.close();
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public FmSiteResponse reopenSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id);
        site.reopen();
        siteRepository.save(site);
        return toResponse(site);
    }

    @Transactional
    public void deleteSite(TenantId tenantId, UUID id) {
        FmSite site = findActive(tenantId, id);
        site.softDelete();
        siteRepository.save(site);
    }

    FmSite findActive(TenantId tenantId, UUID id) {
        return siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", id.toString()));
    }

    private FmSiteResponse toResponse(FmSite s) {
        return new FmSiteResponse(s.getId(), s.getClientId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getNotes(), s.getStatus(), s.getCreatedAt());
    }
}
