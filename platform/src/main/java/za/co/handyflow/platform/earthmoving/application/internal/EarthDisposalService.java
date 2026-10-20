package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.DisposalNoteRequest;
import za.co.handyflow.platform.earthmoving.dto.DisposalResponse;
import za.co.handyflow.platform.earthmoving.dto.RequestDisposalRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Taking a machine out of the fleet. Anyone who can write may ask; an administrator other than the requester approves or rejects; an administrator
 * completes it, which is the only way a machine becomes Retired. Evidence lives in the Document Service. The checks that nothing is still running on the
 * machine are repeated at every step, because things change between asking and finishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthDisposalService {

    private final EarthDisposalRequestRepository requests;
    private final EarthAssetRepository assets;
    private final EarthDeploymentRepository deployments;
    private final EarthHireChargeRepository charges;
    private final EarthStatusEventRepository statusEvents;
    private final DocumentsFacade documents;
    private final EarthAlertService alerts;
    private final AuditTrailFacade trail;
    private final EarthmovingAccess access;

    @Transactional(readOnly = true)
    public List<DisposalResponse> list(TenantId tenantId, String status) {
        access.require(EarthmovingRules.READ, "view disposal requests");
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return requests.findForTenant(tenantId, s).stream().map(r -> toResponse(tenantId, r)).toList();
    }

    @Transactional(readOnly = true)
    public List<DisposalResponse> forAsset(TenantId tenantId, UUID assetId) {
        access.require(EarthmovingRules.READ, "view disposal requests");
        return requests.findByAsset(tenantId, assetId).stream().map(r -> toResponse(tenantId, r)).toList();
    }

    @Transactional(readOnly = true)
    public DisposalResponse get(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.READ, "view disposal requests");
        return toResponse(tenantId, find(tenantId, id));
    }

    @Transactional
    public DisposalResponse request(TenantId tenantId, RequestDisposalRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "ask to dispose of a machine");
        EarthAsset asset = assets.findActiveById(tenantId, req.assetId()).orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(req.assetId())));
        if (requests.findLiveForAsset(tenantId, asset.getId()).isPresent())
            throw new IllegalStateException("There is already a disposal request open for this machine. Finish or cancel it first.");
        DisposalRules.requireNoBlockers(blockers(tenantId, asset));
        EarthDisposalRequest r = requests.save(EarthDisposalRequest.request(tenantId, asset.getId(), req.reason(), req.details(), req.counterparty(), req.proceeds(), actor));
        trail.record(tenantId, "EARTHMOVING_DISPOSAL_REQUESTED", "EARTHMOVING_DISPOSAL", r.getId(), Map.of(),
                Map.of("asset", asset.getName(), "reason", r.getReason(), "details", r.getDetails()));
        alerts.raise(tenantId, DisposalRules.ALERT_TYPE, "WARNING", asset.getId(), "DISPOSAL", r.getId(),
                "Disposal waiting for approval: " + asset.getName(), DisposalRules.statusLine(r.getReason(), r.getDetails()), "DISPOSAL:" + r.getId(), List.of());
        return toResponse(tenantId, r);
    }

    @Transactional
    public DisposalResponse approve(TenantId tenantId, UUID id, DisposalNoteRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "approve a disposal");
        EarthDisposalRequest r = find(tenantId, id);
        EarthAsset asset = assetOf(tenantId, r);
        r.approve(req == null ? null : req.note(), actor);   // checks status and that the approver is not the requester
        DisposalRules.requireEvidence(evidenceCount(tenantId, r));
        DisposalRules.requireNoBlockers(blockers(tenantId, asset));
        trail.record(tenantId, "EARTHMOVING_DISPOSAL_APPROVED", "EARTHMOVING_DISPOSAL", id, Map.of("status", DisposalRules.REQUESTED),
                Map.of("status", r.getStatus(), "note", String.valueOf(r.getDecisionNote())));
        alerts.resolveByKey(tenantId, "DISPOSAL:" + id, actor, "Disposal approved");
        return toResponse(tenantId, r);
    }

    @Transactional
    public DisposalResponse reject(TenantId tenantId, UUID id, DisposalNoteRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "reject a disposal");
        EarthDisposalRequest r = find(tenantId, id);
        r.reject(req == null ? null : req.note(), actor);
        trail.record(tenantId, "EARTHMOVING_DISPOSAL_REJECTED", "EARTHMOVING_DISPOSAL", id, Map.of("status", DisposalRules.REQUESTED),
                Map.of("status", r.getStatus(), "note", r.getDecisionNote()));
        alerts.resolveByKey(tenantId, "DISPOSAL:" + id, actor, "Disposal rejected");
        return toResponse(tenantId, r);
    }

    @Transactional
    public DisposalResponse cancel(TenantId tenantId, UUID id, DisposalNoteRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "cancel a disposal request");
        EarthDisposalRequest r = find(tenantId, id);
        if (!r.getRequestedBy().equals(actor) && !access.has(EarthmovingRules.ADMIN))
            throw new HandyFlowException("Only the person who asked, or an administrator, can cancel this request.", org.springframework.http.HttpStatus.FORBIDDEN, "EARTHMOVING_FORBIDDEN");
        String before = r.getStatus();
        r.cancel(req == null ? null : req.note(), actor);
        trail.record(tenantId, "EARTHMOVING_DISPOSAL_CANCELLED", "EARTHMOVING_DISPOSAL", id, Map.of("status", before),
                Map.of("status", r.getStatus(), "reason", r.getCancelReason()));
        alerts.resolveByKey(tenantId, "DISPOSAL:" + id, actor, "Disposal cancelled");
        return toResponse(tenantId, r);
    }

    /** Retires the machine. Needs an approved request, evidence, and nothing still running on the machine. */
    @Transactional
    public DisposalResponse complete(TenantId tenantId, UUID id, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "complete a disposal");
        EarthDisposalRequest r = find(tenantId, id);
        EarthAsset asset = assetOf(tenantId, r);
        if (!DisposalRules.APPROVED.equals(r.getStatus()))
            throw new IllegalStateException("Only an approved request can be completed. This one is " + r.getStatus().toLowerCase() + ".");
        DisposalRules.requireEvidence(evidenceCount(tenantId, r));
        DisposalRules.requireNoBlockers(blockers(tenantId, asset));
        AssetStatus previous = asset.getStatus();
        asset.retire();
        r.complete(actor);
        assets.save(asset);
        statusEvents.save(EarthStatusEvent.of(tenantId, asset.getId(), previous, AssetStatus.RETIRED,
                DisposalRules.statusLine(r.getReason(), r.getDetails()), actor, asset.getCurrentHours()));
        trail.record(tenantId, "EARTHMOVING_DISPOSAL_COMPLETED", "EARTHMOVING_DISPOSAL", id, Map.of("status", DisposalRules.APPROVED),
                Map.of("status", r.getStatus(), "asset", asset.getName(), "assetStatus", AssetStatus.RETIRED.name()));
        log.info("Disposal completed request={} asset={} actor={}", id, asset.getId(), actor);
        return toResponse(tenantId, r);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EarthDisposalRequest find(TenantId tenantId, UUID id) {
        return requests.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Disposal request", String.valueOf(id)));
    }

    private EarthAsset assetOf(TenantId tenantId, EarthDisposalRequest r) {
        return assets.findActiveById(tenantId, r.getAssetId()).orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(r.getAssetId())));
    }

    private long evidenceCount(TenantId tenantId, EarthDisposalRequest r) {
        return documents.countActive(tenantId, DocumentPolicy.OWNER_DISPOSAL, r.getId(), DocumentPolicy.DISPOSAL_EVIDENCE);
    }

    private List<String> blockers(TenantId tenantId, EarthAsset asset) {
        return DisposalRules.blockers(asset.getStatus(), deployments.findOpenForAsset(tenantId, asset.getId()).isPresent(),
                charges.countPendingForAsset(tenantId, asset.getId()));
    }

    private DisposalResponse toResponse(TenantId tenantId, EarthDisposalRequest r) {
        Optional<EarthAsset> asset = assets.findActiveById(tenantId, r.getAssetId());
        List<String> blockers = r.isLive() && asset.isPresent() ? blockers(tenantId, asset.get()) : List.of();
        return new DisposalResponse(r.getId(), r.getAssetId(), asset.map(EarthAsset::getName).orElse(null), asset.map(EarthAsset::getFleetNumber).orElse(null),
                r.getReason(), r.getStatus(), r.getDetails(), r.getCounterparty(), r.getProceeds(), r.getRequestedBy(), r.getRequestedAt(), r.getDecidedBy(),
                r.getDecidedAt(), r.getDecisionNote(), r.getCompletedBy(), r.getCompletedAt(), r.getCancelledBy(), r.getCancelledAt(), r.getCancelReason(),
                evidenceCount(tenantId, r), blockers);
    }
}
