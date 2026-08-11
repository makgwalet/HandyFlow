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

/**
 * SiteService — CHANGE (V217): getCheckpointQrPayload() updated for the new
 * generateQrPayload(Checkpoint) signature (per-checkpoint secret, no more
 * site-secret param). Added regenerateCheckpointQr() and
 * getSiteCheckpointsForQrSheet() (backs the new QR-sheet PDF). See
 * Checkpoint/CheckpointScanService javadoc for the full rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository          siteRepository;
    private final CheckpointScanService   checkpointScanService;

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

    // ── QR signing / printing (V215, V217) ────────────────────────────────────

    @Transactional(readOnly = true)
    public QrPayloadResponse getCheckpointQrPayload(TenantId tenantId, UUID siteId, UUID checkpointId) {
        Checkpoint checkpoint = findCheckpoint(tenantId, siteId, checkpointId);
        String payload = checkpointScanService.generateQrPayload(checkpoint);
        return new QrPayloadResponse(checkpoint.getId(), checkpoint.getName(), payload);
    }

    @Transactional
    public void setQrEnforcement(TenantId tenantId, UUID siteId, boolean requireSignedQr) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));
        site.setRequireSignedQr(requireSignedQr);
        siteRepository.save(site);
        log.info("[Security] QR HMAC enforcement {} for site={} tenant={}",
                requireSignedQr ? "ENABLED" : "disabled", siteId, tenantId.getValue());
    }

    /**
     * Rotates one checkpoint's QR (both the legacy bare-UUID code and the
     * signing secret) without touching any other checkpoint at the site --
     * see Checkpoint.regenerateQr()'s javadoc. Caller should immediately
     * re-fetch/reprint via getCheckpointQrPayload() or the QR PDF endpoint;
     * the old physical sticker stops working the moment this saves.
     */
    @Transactional
    public QrPayloadResponse regenerateCheckpointQr(TenantId tenantId, UUID siteId, UUID checkpointId) {
        Checkpoint checkpoint = findCheckpoint(tenantId, siteId, checkpointId);
        checkpoint.regenerateQr();
        siteRepository.save(checkpoint.getSite());

        log.warn("[Security] Checkpoint QR regenerated checkpointId={} siteId={} tenant={} "
                        + "— old physical sticker for this checkpoint is now invalid",
                checkpointId, siteId, tenantId.getValue());

        String payload = checkpointScanService.generateQrPayload(checkpoint);
        return new QrPayloadResponse(checkpoint.getId(), checkpoint.getName(), payload);
    }

    /** Backs the "print all checkpoints for this site" QR sheet PDF. */
    @Transactional(readOnly = true)
    public Site getSiteWithCheckpointsForPrinting(TenantId tenantId, UUID siteId) {
        return siteRepository.findActiveByIdWithCheckpoints(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));
    }

    // ── Branch assignment (V218) ──────────────────────────────────────────────

    /**
     * Assigns (or clears, if branchId is null) this site's branch. Does NOT
     * itself change who can see the site -- query-level enforcement isn't
     * wired yet (see Site.branchId's javadoc). This just lets an admin
     * actually set the assignment, which previously wasn't possible at all
     * since the field didn't exist on the entity.
     */
    @Transactional
    public void assignBranch(TenantId tenantId, UUID siteId, UUID branchId) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));
        site.assignBranch(branchId);
        siteRepository.save(site);
        log.info("[Security] Site branch assignment changed site={} branch={} tenant={}",
                siteId, branchId, tenantId.getValue());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Checkpoint findCheckpoint(TenantId tenantId, UUID siteId, UUID checkpointId) {
        Site site = siteRepository.findActiveByIdWithCheckpoints(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));
        return site.getCheckpoints().stream()
                .filter(c -> c.getId().equals(checkpointId) && c.isActive())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint", checkpointId.toString()));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private SiteResponse toResponse(Site s) {
        return new SiteResponse(
                s.getId(), s.getName(), s.getCustomerId(),
                s.getAddress(), s.getLatitude(), s.getLongitude(),
                s.getContactName(), s.getContactPhone(), s.isActive(),
                List.of(),
                s.getContractStatus() != null ? s.getContractStatus() : "ACTIVE",
                s.getContractStart(),
                s.getContractEnd(),
                s.getTerminationReason(),
                s.getTerminatedAt(),
                s.getCreatedAt(),
                s.isRequireSignedQr(),
                s.getBranchId()
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
                s.getTerminatedAt(),
                s.getCreatedAt(),
                s.isRequireSignedQr(),
                s.getBranchId()
        );
    }
}