package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the work order lifecycle. Chunk 4 adds, on top of Chunks 1-3:
 * <ul>
 *   <li><b>Request intake</b> ({@link #raiseRequest}): REQUESTED status, priority resolved by
 *       {@link FacilityPriorityRules}, a possible-duplicate hint, urgent/safety requests notify admins.</li>
 *   <li><b>Approval / rejection</b> with segregation of duties: you cannot approve your own request.</li>
 *   <li><b>Verification</b> (opt-in per work order): completion goes to PENDING_VERIFICATION and needs at
 *       least one piece of evidence; a different person verifies or sends it back for rework. The PPM
 *       schedule only advances once the work order is truly COMPLETED.</li>
 *   <li><b>Cost capture</b>: if itemised cost lines exist, they define the work order's cost on completion.
 *       Downtime and failure cause are recorded at completion.</li>
 * </ul>
 * Segregation rule: the same user may not both request and approve, or both complete and verify. A
 * FACILITIES_ADMIN may override, and the override is written to the audit log as its own entry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityWorkOrderService {

    private static final Set<String> REQUEST_CATEGORIES = Set.of("CORRECTIVE", "EMERGENCY", "INSPECTION", "OTHER");

    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityTechnicianRepository technicianRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityVendorEligibility vendorEligibility;
    private final FacilityWorkOrderCostLineRepository costLineRepository;
    private final FacilityNumberGenerator numberGenerator;
    private final FacilityPpmScheduleService ppmScheduleService;
    private final FacilityAlerts alerts;
    private final EvidenceFacade evidenceFacade;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> getWorkOrders(TenantId tenantId, String status, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        return workOrderRepository.findAllInScope(tenantId, status != null ? status.toUpperCase() : null,
                        scope.allSites(), scope.siteIds(), scope.allJobs(), scope.technicianIds(),
                        scope.allRequesters(), scope.requesterUserId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrder(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    /** Throws NOT FOUND unless the work order exists and is within the caller's scope (used by evidence / PDF / cost endpoints). */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findActive(tenantId, id, accessPolicy.scope(tenantId));
    }

    /** Per-asset history is a planner/manager view; callers limited to their own jobs or requests may not use it. */
    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> getWorkOrdersForAsset(TenantId tenantId, UUID assetId, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly() || scope.requesterOnly()) {
            throw new HandyFlowException("Not permitted for this access level", HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        }
        FacilityAsset asset = assetRepository.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", assetId.toString()));
        scope.requireSite(asset.getSiteId());
        return workOrderRepository.findByAsset(tenantId, assetId, pageable).map(this::toResponse);
    }

    // ── Creation ─────────────────────────────────────────────────────────────

    /** Offline-sync pull: what changed at or after {@code since} that the caller can see, oldest change first. */
    @Transactional(readOnly = true)
    public WorkOrderChangesResponse getChangedSince(TenantId tenantId, java.time.Instant since, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        AccessScope scope = accessPolicy.scope(tenantId);
        List<FacilityWorkOrder> rows = workOrderRepository.findChangedSinceInScope(tenantId, since, scope.allSites(), scope.siteIds(),
                scope.allJobs(), scope.technicianIds(), scope.allRequesters(), scope.requesterUserId(), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<FacilityWorkOrder> page = more ? rows.subList(0, size) : rows;
        java.time.Instant next = page.isEmpty() ? since : page.get(page.size() - 1).getUpdatedAt();
        return new WorkOrderChangesResponse(page.stream().map(this::toResponse).toList(), next, more);
    }

    /** Request intake: the work order starts REQUESTED and cannot be assigned until approved. */
    @Transactional
    public WorkOrderResponse raiseRequest(TenantId tenantId, RaiseWorkRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        requireAssetAtSite(tenantId, req.assetId(), req.siteId());

        String category = req.category() == null || req.category().isBlank() ? "CORRECTIVE" : req.category().trim().toUpperCase();
        if (!REQUEST_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("category for a request must be one of " + REQUEST_CATEGORIES);
        }
        String priority = FacilityPriorityRules.resolve(req.priority(), category, req.safetyImpact());

        UUID userId = TenantContext.getCurrentUserId();
        String reportedBy = req.reportedBy() != null && !req.reportedBy().isBlank()
                ? req.reportedBy().trim() : TenantContext.getCurrentUserName();

        UUID duplicateOf = null;
        if (req.assetId() != null) {
            List<UUID> open = workOrderRepository.findOpenReactiveIdsForAsset(tenantId, req.assetId(), PageRequest.of(0, 1));
            if (!open.isEmpty()) duplicateOf = open.get(0);
        }

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FacilityWorkOrder wo = FacilityWorkOrder.createRequest(tenantId, number, req.siteId(), req.assetId(), category,
                priority, req.description(), reportedBy, userId, req.safetyImpact(), duplicateOf);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_RAISED", "WORK_ORDER", wo.getId(),
                number + " priority=" + priority + " safety=" + req.safetyImpact()
                        + (duplicateOf != null ? " possibleDuplicateOf=" + duplicateOf : ""));
        log.info("Work request raised number={} tenant={}", number, tenantId);

        if (req.safetyImpact() || "URGENT".equals(priority) || "EMERGENCY".equals(priority)) {
            notifyUrgent(tenantId, wo, req.safetyImpact() ? "Safety-related request" : "Urgent request");
        } else {
            notifyRequested(tenantId, wo);
        }
        return toResponse(wo);
    }

    /** Direct creation by a manager — starts OPEN, no approval step. */
    @Transactional
    public WorkOrderResponse createWorkOrder(TenantId tenantId, CreateWorkOrderRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        requireAssetAtSite(tenantId, req.assetId(), req.siteId());

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FacilityWorkOrder wo = FacilityWorkOrder.create(tenantId, number, req.siteId(), req.assetId(), null,
                req.category(), req.priority(), req.description(), req.reportedBy(), req.scheduledDate());
        if (Boolean.TRUE.equals(req.requiresVerification())) wo.requireVerification(true);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_CREATED", "WORK_ORDER", wo.getId(),
                number + " category=" + wo.getCategory() + " priority=" + wo.getPriority() + " site=" + req.siteId());
        log.info("Work order created number={} tenant={}", number, tenantId);

        if ("EMERGENCY".equals(wo.getPriority()) || "URGENT".equals(wo.getPriority())) {
            notifyUrgent(tenantId, wo, (wo.getPriority().equals("EMERGENCY") ? "EMERGENCY" : "Urgent") + " work order");
        }
        return toResponse(wo);
    }

    /** System path used by {@link FacilityNotificationScheduler} — not user-scoped. */
    @Transactional
    public FacilityWorkOrder createFromPpmSchedule(TenantId tenantId, UUID assetId, UUID siteId,
                                                   UUID ppmScheduleId, String taskName, LocalDate dueDate) {
        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FacilityWorkOrder wo = FacilityWorkOrder.create(tenantId, number, siteId, assetId, ppmScheduleId,
                "PPM", "NORMAL", "Planned preventive maintenance: " + taskName, "System (PPM schedule)", dueDate);
        workOrderRepository.save(wo);
        log.info("PPM work order generated number={} schedule={} tenant={}", number, ppmScheduleId, tenantId);
        return wo;
    }

    // ── Triage ───────────────────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse approve(TenantId tenantId, UUID id, ApproveWorkRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        UUID approver = TenantContext.getCurrentUserId();
        enforceSegregation(tenantId, wo, wo.getRequestedByUserId(), approver, "approve", "REQUEST_SELF_APPROVED");

        String priority = req != null && req.priority() != null
                ? FacilityPriorityRules.resolve(req.priority(), wo.getCategory(), wo.isSafetyImpact()) : null;
        wo.approve(approver, priority, req != null ? req.scheduledDate() : null,
                req != null ? req.requiresVerification() : null);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_APPROVED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " priority=" + wo.getPriority() + " requiresVerification=" + wo.isRequiresVerification());
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse reject(TenantId tenantId, UUID id, String reason) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.reject(TenantContext.getCurrentUserId(), reason);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_REJECTED", "WORK_ORDER", id, wo.getWorkOrderNumber() + " reason=" + reason);
        return toResponse(wo);
    }

    // ── Execution ────────────────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse assign(TenantId tenantId, UUID id, AssignWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));

        String technicianName = null;
        if (req.technicianId() != null) {
            FacilityTechnician t = technicianRepository.findActiveById(tenantId, req.technicianId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityTechnician", req.technicianId().toString()));
            if (!t.isActive()) {
                throw new IllegalStateException("Technician " + t.getName() + " is deactivated and cannot be assigned work");
            }
            technicianName = t.getName();
        }

        String vendorName = null;
        String overrideNote = null;
        if (req.vendorId() != null) {
            FacilityVendor v = vendorRepository.findActiveById(tenantId, req.vendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityVendor", req.vendorId().toString()));
            if (!v.isActive()) {
                throw new IllegalStateException("Vendor " + v.getCompanyName() + " is deactivated and cannot be assigned work");
            }
            vendorName = v.getCompanyName();
            List<String> problems = vendorEligibility.problems(tenantId, v);
            if (!problems.isEmpty()) {
                if (!accessPolicy.hasAuthority("FACILITIES_ADMIN")) {
                    throw new HandyFlowException("Cannot assign work to " + vendorName + ": " + String.join("; ", problems),
                            HttpStatus.CONFLICT, "VENDOR_NOT_ELIGIBLE");
                }
                overrideNote = String.join("; ", problems);
            }
        }

        wo.assign(req.technicianId(), technicianName, req.vendorId(), vendorName);
        workOrderRepository.save(wo);
        auditService.record(tenantId, overrideNote != null ? "WORK_ORDER_ASSIGNED_INELIGIBLE_VENDOR" : "WORK_ORDER_ASSIGNED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " technician=" + technicianName + " vendor=" + vendorName
                        + (overrideNote != null ? " ADMIN OVERRIDE: " + overrideNote : ""));
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse start(TenantId tenantId, UUID id) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.start();
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_STARTED", "WORK_ORDER", id, wo.getWorkOrderNumber());
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse putOnHold(TenantId tenantId, UUID id, HoldWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.putOnHold(req.reason());
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_ON_HOLD", "WORK_ORDER", id, wo.getWorkOrderNumber() + " reason=" + req.reason());
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse complete(TenantId tenantId, UUID id, CompleteWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));

        if (wo.isRequiresVerification()
                && evidenceFacade.listFor(tenantId, "facilities", "FacilityWorkOrder", id).isEmpty()) {
            throw new IllegalStateException(
                    "Attach at least one photo or document as evidence before completing a job that requires verification");
        }

        BigDecimal lineTotal = costLineRepository.sumForWorkOrder(tenantId, id);
        BigDecimal cost = lineTotal != null ? lineTotal : req.cost();

        wo.complete(req.completionNotes(), cost, req.completedAt(), TenantContext.getCurrentUserId(),
                req.downtimeMinutes(), req.failureCause());
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_COMPLETED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " status=" + wo.getStatus() + " cost=" + cost
                        + (lineTotal != null ? " (from cost lines)" : "") + " downtimeMinutes=" + req.downtimeMinutes());
        log.info("Work order completed number={} status={} tenant={}", wo.getWorkOrderNumber(), wo.getStatus(), tenantId);

        if ("COMPLETED".equals(wo.getStatus())) advancePpmSchedule(tenantId, wo);
        else if ("PENDING_VERIFICATION".equals(wo.getStatus())) notifyAwaitingVerification(tenantId, wo);
        return toResponse(wo);
    }

    // ── Verification ─────────────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse verify(TenantId tenantId, UUID id) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        UUID verifier = TenantContext.getCurrentUserId();
        enforceSegregation(tenantId, wo, wo.getCompletedByUserId(), verifier, "verify", "WORK_SELF_VERIFIED");

        wo.verify(verifier);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_VERIFIED", "WORK_ORDER", id, wo.getWorkOrderNumber() + " cost=" + wo.getCost());
        advancePpmSchedule(tenantId, wo);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse rejectVerification(TenantId tenantId, UUID id, String reason) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.rejectVerification(reason);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_REWORK_REQUESTED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " rework#" + wo.getReworkCount() + " reason=" + reason);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse cancel(TenantId tenantId, UUID id, CancelWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.cancel(req.reason());
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_CANCELLED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " reason=" + req.reason());
        return toResponse(wo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * The same person may not be both originator and decider. A FACILITIES_ADMIN may override; that is
     * recorded as a separate audit entry so overrides are easy to find.
     */
    private void enforceSegregation(TenantId tenantId, FacilityWorkOrder wo, UUID originator, UUID actor,
                                    String verb, String overrideAction) {
        if (originator == null || !originator.equals(actor)) return;
        if (accessPolicy.hasAuthority("FACILITIES_ADMIN")) {
            auditService.record(tenantId, overrideAction, "WORK_ORDER", wo.getId(),
                    wo.getWorkOrderNumber() + " — administrator override of segregation of duties");
            return;
        }
        throw new HandyFlowException("You cannot " + verb + " your own work — another person must do this",
                HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");
    }

    private void advancePpmSchedule(TenantId tenantId, FacilityWorkOrder wo) {
        if (wo.getPpmScheduleId() == null) return;
        LocalDate completedDate = wo.getCompletedAt() != null
                ? FacilityTime.toLocalDate(wo.getCompletedAt())
                : FacilityTime.today();
        ppmScheduleService.applyCompletion(tenantId, wo.getPpmScheduleId(), completedDate);
    }

    private void requireAssetAtSite(TenantId tenantId, UUID assetId, UUID siteId) {
        if (assetId == null) return;
        FacilityAsset asset = assetRepository.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", assetId.toString()));
        if (!asset.getSiteId().equals(siteId)) {
            throw new IllegalArgumentException("The selected asset does not belong to the selected site");
        }
    }

    private void notifyUrgent(TenantId tenantId, FacilityWorkOrder wo, String titlePrefix) {
        alerts.toAdmins(tenantId, NotificationType.FACILITY_WORKORDER_URGENT, titlePrefix + ": " + wo.getWorkOrderNumber(),
                wo.getDescription(), "/facilities/work-orders/" + wo.getId(), wo.getId().toString());
    }

    private void notifyRequested(TenantId tenantId, FacilityWorkOrder wo) {
        alerts.toAdmins(tenantId, NotificationType.FACILITY_WORKORDER_REQUESTED, "Work request submitted: " + wo.getWorkOrderNumber(),
                "A " + wo.getPriority() + " request is waiting for approval: " + wo.getDescription(),
                "/facilities/work-orders/" + wo.getId(), wo.getId().toString());
    }

    private void notifyAwaitingVerification(TenantId tenantId, FacilityWorkOrder wo) {
        alerts.toAdmins(tenantId, NotificationType.FACILITY_WORKORDER_AWAITING_VERIFICATION,
                "Awaiting verification: " + wo.getWorkOrderNumber(),
                wo.getWorkOrderNumber() + " has been completed and needs checking by someone other than the person who did the work.",
                "/facilities/work-orders/" + wo.getId(), wo.getId().toString());
    }

    private FacilityWorkOrder findActive(TenantId tenantId, UUID id, AccessScope scope) {
        FacilityWorkOrder wo = workOrderRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityWorkOrder", id.toString()));
        if (!scope.canSeeWorkOrder(wo)) {
            throw new ResourceNotFoundException("FacilityWorkOrder", id.toString());
        }
        return wo;
    }

    private WorkOrderResponse toResponse(FacilityWorkOrder w) {
        return new WorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getSiteId(), w.getAssetId(),
                w.getPpmScheduleId(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(),
                w.getReportedBy(), w.getTechnicianId(), w.getTechnicianName(), w.getVendorId(), w.getVendorName(),
                w.getScheduledDate(), w.getCompletedAt(), w.getCompletionNotes(), w.getHoldReason(), w.getCost(),
                w.getCancellationReason(), w.getCreatedAt(),
                w.getRequestedByUserId(), w.isSafetyImpact(), w.getDuplicateOfWorkOrderId(), w.getApprovedAt(),
                w.getRejectionReason(), w.isRequiresVerification(), w.getVerifiedAt(),
                w.getReworkCount(), w.getLastReworkReason(), w.getDowntimeMinutes(), w.getFailureCause());
    }
}