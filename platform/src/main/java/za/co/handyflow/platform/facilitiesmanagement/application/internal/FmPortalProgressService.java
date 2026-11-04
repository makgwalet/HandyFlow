package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalDataService.Scope;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalAssetResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalPhotoResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalProgressResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.UUID;

/** Client-safe reads for the portal: the client's assets, how a job is progressing, and its photos. Every read is scoped by the caller's grants. */
@Service
@RequiredArgsConstructor
public class FmPortalProgressService {

    private static final String SOURCE = "facilitiesmanagement";

    private final FmPortalDataService data;
    private final FmSiteRepository siteRepo;
    private final FmAssetRepository assetRepo;
    private final FmWorkOrderRepository workOrderRepo;
    private final EvidenceFacade evidence;

    @Transactional(readOnly = true)
    public List<FmPortalAssetResponse> assets(UUID portalUserId, UUID clientId) {
        Scope s = data.resolveScope(portalUserId, clientId);
        List<UUID> siteIds = siteRepo.findAllActiveForClient(s.tenantId(), s.clientId(), org.springframework.data.domain.Pageable.unpaged())
                .stream().map(FmSite::getId).toList();
        if (siteIds.isEmpty()) return List.of();
        return assetRepo.findActiveInSites(s.tenantId(), siteIds).stream().map(FmPortalProgressService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FmPortalProgressResponse progress(UUID portalUserId, UUID clientId, UUID workOrderId) {
        Scope s = data.resolveScope(portalUserId, clientId);
        FmWorkOrder w = visibleOrder(s, workOrderId);
        boolean hideDone = "PENDING_VERIFICATION".equals(w.getStatus());
        return new FmPortalProgressResponse(FmPortalStatus.present(w.getStatus()),
                FmPortalProgress.steps(w.getStatus(), w.getCreatedAt(), w.getApprovedAt(), w.getScheduledDate(),
                        hideDone ? null : w.getCompletedAt()));
    }

    @Transactional(readOnly = true)
    public List<FmPortalPhotoResponse> photos(UUID portalUserId, UUID clientId, UUID workOrderId) {
        Scope s = data.resolveScope(portalUserId, clientId);
        FmWorkOrder w = visibleOrder(s, workOrderId);
        return evidence.listFor(s.tenantId(), SOURCE, "FmWorkOrder", w.getId()).stream()
                .filter(e -> FmPortalProgress.photoVisible(e.evidenceType(), e.contentType(), w.getStatus()))
                .map(e -> new FmPortalPhotoResponse(e.id(), e.fileName(), e.evidenceType(), e.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence photo(UUID portalUserId, UUID clientId, UUID workOrderId, UUID evidenceId) {
        Scope s = data.resolveScope(portalUserId, clientId);
        FmWorkOrder w = visibleOrder(s, workOrderId);
        boolean allowed = evidence.listFor(s.tenantId(), SOURCE, "FmWorkOrder", w.getId()).stream()
                .filter(e -> e.id().equals(evidenceId))
                .anyMatch(e -> FmPortalProgress.photoVisible(e.evidenceType(), e.contentType(), w.getStatus()));
        if (!allowed) throw notFound();
        return evidence.download(s.tenantId(), evidenceId);
    }

    /** A job of this client that the client is allowed to see (staff-raised unapproved requests are hidden, as in the list). */
    private FmWorkOrder visibleOrder(Scope s, UUID id) {
        FmWorkOrder w = workOrderRepo.findActiveById(s.tenantId(), id).orElseThrow(FmPortalProgressService::notFound);
        boolean mine = s.clientId().equals(w.getClientId());
        boolean hiddenStaffRequest = ("REQUESTED".equals(w.getStatus()) || "REJECTED".equals(w.getStatus())) && w.getRequestedByPortalUserId() == null;
        if (!mine || hiddenStaffRequest) throw notFound();
        return w;
    }

    private static HandyFlowException notFound() {
        return new HandyFlowException("That work order was not found", HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND");
    }

    private static FmPortalAssetResponse toResponse(FmAsset a) {
        return new FmPortalAssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(), a.getLocation(),
                a.getManufacturer(), a.getModel(), a.getStatus(), a.getInstallDate(), a.getWarrantyExpiryDate());
    }
}
