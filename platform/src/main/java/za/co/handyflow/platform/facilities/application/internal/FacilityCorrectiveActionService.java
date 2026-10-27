package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.CorrectiveActionRequest;
import za.co.handyflow.platform.facilities.dto.CorrectiveActionResponse;
import za.co.handyflow.platform.facilities.dto.CreateWorkOrderRequest;
import za.co.handyflow.platform.facilities.dto.WorkOrderResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Corrective actions: things that must be put right. Created automatically for every failed inspection check,
 * or logged by hand. Every change is audited; scope follows the caller's site access (out-of-scope = not found).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityCorrectiveActionService {

    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityWorkOrderService workOrderService;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<CorrectiveActionResponse> list(TenantId tenantId, UUID siteId, String status, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) scope.requireSite(siteId);
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return actionRepository.findAllInScope(tenantId, siteId, s, scope.allSites(), scope.siteIds(), pageable)
                .map(a -> toResponse(a, FacilityTime.today()));
    }

    @Transactional(readOnly = true)
    public CorrectiveActionResponse get(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id), FacilityTime.today());
    }

    @Transactional
    public CorrectiveActionResponse create(TenantId tenantId, CorrectiveActionRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        if (req.assetId() != null) {
            FacilityAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId()))
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
        }
        String source = req.sourceType() == null || req.sourceType().isBlank() ? "MANUAL" : req.sourceType().trim().toUpperCase();
        if ("INSPECTION".equals(source))
            throw new IllegalArgumentException("Actions for failed inspection checks are raised automatically when the inspection is completed");
        if ("CERTIFICATE".equals(source)) {
            if (req.sourceId() == null) throw new IllegalArgumentException("sourceId (the certificate) is required for a CERTIFICATE action");
            FacilityComplianceCertificate cert = certificateRepository.findByIdForTenant(tenantId, req.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityComplianceCertificate", req.sourceId().toString()));
            if (!cert.getSiteId().equals(req.siteId()))
                throw new IllegalArgumentException("The certificate does not belong to the selected site");
        }
        FacilityCorrectiveAction a = FacilityCorrectiveAction.create(tenantId, source, req.sourceId(), req.siteId(), req.assetId(),
                req.description(), req.priority(), false, req.dueDate(), TenantContext.getCurrentUserId());
        actionRepository.save(a);
        auditService.record(tenantId, "ACTION_CREATED", "CORRECTIVE_ACTION", a.getId(),
                "source=" + source + " site=" + a.getSiteId() + " priority=" + a.getPriority() + " due=" + a.getDueDate());
        return toResponse(a, FacilityTime.today());
    }

    /**
     * Called when an inspection is completed: one action per failed check. The inspection has already been
     * scope-checked by the caller. Returns the new action ids.
     */
    @Transactional
    public List<UUID> createFromFailures(TenantId tenantId, FacilityInspection inspection) {
        LocalDate today = FacilityTime.today();
        List<UUID> ids = new ArrayList<>();
        for (FacilityInspectionResult r : inspection.failures()) {
            String description = inspection.getTemplateName() + " — failed: " + r.getPrompt()
                    + (r.getComment() != null ? " (" + r.getComment() + ")" : "");
            if (description.length() > 1000) description = description.substring(0, 997) + "...";
            FacilityCorrectiveAction a = FacilityCorrectiveAction.create(tenantId, "INSPECTION", inspection.getId(),
                    inspection.getSiteId(), inspection.getAssetId(), description,
                    FacilityActionRules.priorityForFailure(r.isCritical()), r.isCritical(),
                    FacilityActionRules.dueDateForFailure(r.isCritical(), today), inspection.getInspectorUserId());
            actionRepository.save(a);
            auditService.record(tenantId, "ACTION_CREATED", "CORRECTIVE_ACTION", a.getId(),
                    "source=INSPECTION inspection=" + inspection.getId() + " critical=" + r.isCritical() + " due=" + a.getDueDate());
            ids.add(a.getId());
        }
        return ids;
    }

    /**
     * Called by the daily sweep when a certificate lapses: one CRITICAL action to renew it. Safe to call again for
     * the same certificate (does nothing while an unfinished action for it exists). No signed-in user, no scope check.
     * Returns the new action id, or empty when one was already open.
     */
    @Transactional
    public Optional<UUID> raiseForLapsedCertificate(FacilityComplianceCertificate cert) {
        TenantId tenantId = cert.getTenantId();
        boolean alreadyOpen = actionRepository.findBySource(tenantId, "CERTIFICATE", cert.getId()).stream()
                .anyMatch(a -> !a.isTerminal());
        if (alreadyOpen) return Optional.empty();
        String label = cert.getCertificateType() + (cert.getCertificateNumber() != null ? " " + cert.getCertificateNumber() : "");
        String description = "Certificate lapsed: " + label + " expired on " + cert.getExpiryDate()
                + ". Renew or replace it, then close this action.";
        FacilityCorrectiveAction a = FacilityCorrectiveAction.create(tenantId, "CERTIFICATE", cert.getId(), cert.getSiteId(),
                cert.getAssetId(), description, "URGENT", true,
                FacilityActionRules.dueDateForLapsedCertificate(FacilityTime.today()), null);
        actionRepository.save(a);
        auditService.recordSystem(tenantId, "ACTION_CREATED", "CORRECTIVE_ACTION", a.getId(),
                "source=CERTIFICATE certificate=" + cert.getId() + " due=" + a.getDueDate());
        return Optional.of(a.getId());
    }

    /** Raise a (directly created, OPEN) corrective work order for this action and link it. */
    @Transactional
    public CorrectiveActionResponse raiseWorkOrder(TenantId tenantId, UUID id) {
        FacilityCorrectiveAction a = find(tenantId, id);
        if (a.isTerminal()) throw new IllegalStateException("This action is already " + a.getStatus());
        if (a.getWorkOrderId() != null) throw new IllegalStateException("A work order has already been raised for this action");
        WorkOrderResponse wo = workOrderService.createWorkOrder(tenantId, new CreateWorkOrderRequest(
                a.getSiteId(), a.getAssetId(), "CORRECTIVE", a.getPriority(), a.getDescription(),
                "Corrective action", a.getDueDate(), a.isCritical()));
        a.linkWorkOrder(wo.id());
        actionRepository.save(a);
        auditService.record(tenantId, "ACTION_WORK_ORDER_RAISED", "CORRECTIVE_ACTION", id, "workOrder=" + wo.workOrderNumber());
        return toResponse(a, FacilityTime.today());
    }

    @Transactional
    public CorrectiveActionResponse close(TenantId tenantId, UUID id, String note) {
        FacilityCorrectiveAction a = find(tenantId, id);
        if (a.getWorkOrderId() != null && !a.isTerminal()) {
            FacilityWorkOrder wo = workOrderRepository.findActiveById(tenantId, a.getWorkOrderId()).orElse(null);
            if (wo != null && !"COMPLETED".equals(wo.getStatus())) {
                throw new HandyFlowException("The linked work order " + wo.getWorkOrderNumber() + " is " + wo.getStatus()
                        + " — finish it first, or cancel this action", HttpStatus.CONFLICT, "WORK_ORDER_NOT_COMPLETE");
            }
        }
        a.close(note);
        actionRepository.save(a);
        auditService.record(tenantId, "ACTION_CLOSED", "CORRECTIVE_ACTION", id, "note=" + a.getClosureNote());
        return toResponse(a, FacilityTime.today());
    }

    @Transactional
    public CorrectiveActionResponse cancel(TenantId tenantId, UUID id, String reason) {
        FacilityCorrectiveAction a = find(tenantId, id);
        a.cancel(reason);
        actionRepository.save(a);
        auditService.record(tenantId, "ACTION_CANCELLED", "CORRECTIVE_ACTION", id, "reason=" + a.getClosureNote());
        return toResponse(a, FacilityTime.today());
    }

    @Transactional
    public CorrectiveActionResponse reschedule(TenantId tenantId, UUID id, LocalDate dueDate) {
        FacilityCorrectiveAction a = find(tenantId, id);
        LocalDate before = a.getDueDate();
        a.reschedule(dueDate);
        actionRepository.save(a);
        auditService.record(tenantId, "ACTION_RESCHEDULED", "CORRECTIVE_ACTION", id, "from=" + before + " to=" + dueDate);
        return toResponse(a, FacilityTime.today());
    }

    private FacilityCorrectiveAction find(TenantId tenantId, UUID id) {
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityCorrectiveAction a = actionRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityCorrectiveAction", id.toString()));
        if (!scope.canSeeSite(a.getSiteId())) throw new ResourceNotFoundException("FacilityCorrectiveAction", id.toString());
        return a;
    }

    static CorrectiveActionResponse toResponse(FacilityCorrectiveAction a, LocalDate today) {
        return new CorrectiveActionResponse(a.getId(), a.getSourceType(), a.getSourceId(), a.getSiteId(), a.getAssetId(),
                a.getDescription(), a.getPriority(), a.isCritical(), a.getStatus(), a.getDueDate(), a.isOverdue(today),
                a.getWorkOrderId(), a.getClosureNote(), a.getClosedAt(), a.getCreatedAt());
    }
}
