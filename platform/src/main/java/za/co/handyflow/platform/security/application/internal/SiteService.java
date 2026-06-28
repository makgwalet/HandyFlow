// security/application/internal/SiteService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SiteResponse> getSites(TenantId tenantId, Pageable pageable) {
        return siteRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SiteResponse getSite(TenantId tenantId, UUID id) {
        return siteRepository.findActiveByIdWithCheckpoints(tenantId, id)
                .map(this::toResponseWithCheckpoints)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id.toString()));
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public SiteResponse createSite(TenantId tenantId, CreateSiteRequest req) {
        Site site = Site.create(tenantId, req.customerId(), req.name(),
                req.address(), req.latitude(), req.longitude(),
                req.contactName(), req.contactPhone(), req.instructions());
        siteRepository.save(site);
        log.info("[Security] Created site='{}' tenant={}", site.getName(), tenantId);
        return toResponse(site);
    }

    @Transactional
    public SiteResponse addCheckpoint(TenantId tenantId, UUID siteId,
                                      CreateCheckpointRequest req) {
        Site site = siteRepository.findActiveByIdWithCheckpoints(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));

        Checkpoint cp = Checkpoint.create(tenantId, site, req.name(),
                req.description(), site.getCheckpoints().size());
        site.getCheckpoints().add(cp);
        siteRepository.save(site);
        return toResponseWithCheckpoints(site);
    }

    @Transactional
    public void deleteSite(TenantId tenantId, UUID id, UUID deletedBy) {
        Site site = siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id.toString()));
        // Fix bug #19 pattern: pass actor ID, not null
        site.softDelete(deletedBy);
        siteRepository.save(site);
        log.info("[Security] Soft-deleted site={} by={} tenant={}", id, deletedBy, tenantId);
    }

    @Transactional
    public SiteResponse terminateSite(TenantId tenantId, UUID id, String reason, UUID terminatedBy) {
        Site site = siteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id.toString()));
        site.terminateContract(reason != null ? reason : "Contract ended");
        siteRepository.save(site);
        log.info("[Security] Contract terminated site={} by={} reason='{}' tenant={}",
                id, terminatedBy, reason, tenantId);
        return toResponse(site);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    /**
     * Maps a Site to SiteResponse.
     *
     * Fixes bug #9: terminatedAt was captured in Site.java (V48 migration added
     * the column; Site.java has the field) but was never included in SiteResponse
     * or the mapper.  Every API consumer was blind to WHEN a contract was terminated
     * — only the reason was visible.  For audit and legal purposes, the timestamp
     * is equally important.
     */
    private SiteResponse toResponse(Site s) {
        return new SiteResponse(
                s.getId(), s.getName(), s.getCustomerId(),
                s.getAddress(), s.getLatitude(), s.getLongitude(),
                s.getContactName(), s.getContactPhone(), s.isActive(),
                List.of(),  // no checkpoints in list view — saves N+1 lazy loads
                s.getContractStatus() != null ? s.getContractStatus() : "ACTIVE",
                s.getContractStart(),
                s.getContractEnd(),
                s.getTerminationReason(),
                s.getTerminatedAt(),     // ← bug #9 fix: was missing from all previous toResponse calls
                s.getCreatedAt()
        );
    }

    private SiteResponse toResponseWithCheckpoints(Site s) {
        List<CheckpointResponse> cps = s.getCheckpoints().stream()
                .filter(Checkpoint::isActive)
                .map(c -> new CheckpointResponse(
                        c.getId(), c.getName(), c.getDescription(),
                        c.getQrCode(), c.getSortOrder()
                )).toList();

        return new SiteResponse(
                s.getId(), s.getName(), s.getCustomerId(),
                s.getAddress(), s.getLatitude(), s.getLongitude(),
                s.getContactName(), s.getContactPhone(), s.isActive(),
                cps,
                s.getContractStatus() != null ? s.getContractStatus() : "ACTIVE",
                s.getContractStart(),
                s.getContractEnd(),
                s.getTerminationReason(),
                s.getTerminatedAt(),     // ← bug #9 fix
                s.getCreatedAt()
        );
    }
}
