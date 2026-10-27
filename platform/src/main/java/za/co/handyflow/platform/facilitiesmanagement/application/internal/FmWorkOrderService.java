package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the work order lifecycle. Chunk 6 adds, on top of Chunks 2 and 5:
 * <ul>
 *   <li><b>Request intake</b> ({@link #raiseRequest}): REQUESTED status, priority via {@link FmPriorityRules},
 *       a possible-duplicate hint, urgent/safety requests notify admins.</li>
 *   <li><b>Approval / rejection</b> with segregation of duties: you cannot approve your own request.</li>
 *   <li><b>Verification</b> (opt-in per work order): completion goes to PENDING_VERIFICATION and needs at
 *       least one piece of evidence; a different person verifies or sends it back for rework. The PPM
 *       schedule advances, and the job becomes billable, only once it is truly COMPLETED.</li>
 *   <li><b>Cost capture</b>: if itemised cost lines exist, the sum of the BILLABLE lines becomes the work
 *       order's cost on completion (this is the amount billed in time-and-materials invoicing). Downtime and
 *       failure cause are recorded at completion.</li>
 * </ul>
 * Segregation rule: the same user may not both request and approve, or both complete and verify. A
 * FACILITIESMANAGEMENT_ADMIN may override; the override is written to the audit log as its own entry.
 * Reads/writes remain filtered through the caller's {@link FmAccessScope}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmWorkOrderService {

    private static final Set<String> REQUEST_CATEGORIES = Set.of("CORRECTIVE", "EMERGENCY", "INSPECTION", "OTHER");

    private final FmWorkOrderRepository workOrderRepository;
    private final FmClientRepository clientRepository;
    private final FmSiteRepository siteRepository;
    private final FmAssetRepository assetRepository;
    private final FmTechnicianRepository technicianRepository;
    private final FmVendorRepository vendorRepository;
    private final FmWorkOrderCostLineRepository costLineRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmPpmScheduleService ppmScheduleService;
    private final FmAlerts alerts;
    private final EvidenceFacade evidenceFacade;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;
    private final FmContractTermsService contractTermsService;
    private final FmVendorEligibility vendorEligibility;

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FmWorkOrderResponse> getWorkOrders(TenantId tenantId, UUID clientId, String status, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        String normalizedStatus = status != null ? status.toUpperCase() : null;
        if (clientId != null) {
            if (!scope.assignedOnly()) scope.requireClient(clientId); // technicians are narrowed by assignment instead
            return workOrderRepository.findAllForClientInScope(tenantId, clientId, normalizedStatus,
                    scope.allJobs(), scope.technicianIds(), pageable).map(this::toResponse);
        }
        return workOrderRepository.findAllInScope(tenantId, normalizedStatus, scope.workOrderClientFilterOff(),
                scope.clientIds(), scope.allJobs(), scope.technicianIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmWorkOrderResponse getWorkOrder(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    /** Throws NOT FOUND unless the work order exists and is within the caller's scope (used by evidence / PDF / cost endpoints). */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findActive(tenantId, id, accessPolicy.scope(tenantId));
    }

    /** Offline-sync pull: what changed at or after {@code since} that the caller can see, oldest change first. */
    @Transactional(readOnly = true)
    public FmWorkOrderChangesResponse getChangedSince(TenantId tenantId, Instant since, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        FmAccessScope scope = accessPolicy.scope(tenantId);
        List<FmWorkOrder> rows = workOrderRepository.findChangedSinceInScope(tenantId, since, scope.workOrderClientFilterOff(),
                scope.clientIds(), scope.allJobs(), scope.technicianIds(), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<FmWorkOrder> page = more ? rows.subList(0, size) : rows;
        Instant next = page.isEmpty() ? since : page.get(page.size() - 1).getUpdatedAt();
        return new FmWorkOrderChangesResponse(page.stream().map(this::toResponse).toList(), next, more);
    }

    /** Per-asset history is a planner/manager view; a technician limited to their own jobs may not use it. */
    @Transactional(readOnly = true)
    public Page<FmWorkOrderResponse> getWorkOrdersForAsset(TenantId tenantId, UUID assetId, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly()) {
            throw new HandyFlowException("Not permitted for field-technician access", HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        }
        FmAsset asset = assetRepository.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", assetId.toString()));
        scope.requireSite(asset.getSiteId());
        return workOrderRepository.findByAsset(tenantId, assetId, pageable).map(this::toResponse);
    }

    // ── Creation ─────────────────────────────────────────────────────────────

    /** Request intake: the work order starts REQUESTED and cannot be assigned until approved. */
    @Transactional
    public FmWorkOrderResponse raiseRequest(TenantId tenantId, RaiseFmWorkRequest req) {
        validateClientSiteAsset(tenantId, req.clientId(), req.siteId(), req.assetId());

        String category = req.category() == null || req.category().isBlank() ? "CORRECTIVE" : req.category().trim().toUpperCase();
        if (!REQUEST_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("category for a request must be one of " + REQUEST_CATEGORIES);
        }
        String priority = FmPriorityRules.resolve(req.priority(), category, req.safetyImpact());

        UUID userId = TenantContext.getCurrentUserId();
        String reportedBy = req.reportedBy() != null && !req.reportedBy().isBlank()
                ? req.reportedBy().trim() : TenantContext.getCurrentUserName();

        UUID duplicateOf = null;
        if (req.assetId() != null) {
            List<UUID> open = workOrderRepository.findOpenReactiveIdsForAsset(tenantId, req.assetId(), PageRequest.of(0, 1));
            if (!open.isEmpty()) duplicateOf = open.get(0);
        }

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.createRequest(tenantId, number, req.clientId(), req.siteId(), req.assetId(),
                category, priority, req.description(), reportedBy, userId, req.safetyImpact(), duplicateOf);
        contractTermsService.applyTerms(tenantId, wo);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_RAISED", "WORK_ORDER", wo.getId(),
                number + " client=" + req.clientId() + " priority=" + priority + " safety=" + req.safetyImpact()
                        + (duplicateOf != null ? " possibleDuplicateOf=" + duplicateOf : ""));
        log.info("FM work request raised number={} client={} tenant={}", number, req.clientId(), tenantId);

        if (req.safetyImpact() || "URGENT".equals(priority) || "EMERGENCY".equals(priority)) {
            notifyUrgent(tenantId, wo, req.safetyImpact() ? "Safety-related request" : "Urgent request");
        } else {
            notifyRequested(tenantId, wo);
        }
        return toResponse(wo);
    }

    /** Direct creation by a manager — starts OPEN, no approval step. */
    @Transactional
    public FmWorkOrderResponse createWorkOrder(TenantId tenantId, CreateFmWorkOrderRequest req) {
        validateClientSiteAsset(tenantId, req.clientId(), req.siteId(), req.assetId());

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.create(tenantId, number, req.clientId(), req.siteId(), req.assetId(), null,
                req.category(), req.priority(), req.description(), req.reportedBy(), req.scheduledDate());
        if (Boolean.TRUE.equals(req.requiresVerification())) wo.requireVerification(true);
        contractTermsService.applyTerms(tenantId, wo);
        workOrderRepository.save(wo);
        log.info("FM work order created number={} client={} tenant={}", number, req.clientId(), tenantId);

        if ("EMERGENCY".equals(wo.getPriority()) || "URGENT".equals(wo.getPriority())) {
            notifyUrgent(tenantId, wo, (wo.getPriority().equals("EMERGENCY") ? "EMERGENCY" : "Urgent") + " work order");
        }
        return toResponse(wo);
    }

    /** System path used by {@link FmNotificationScheduler} — not user-scoped. */
    @Transactional
    public FmWorkOrder createFromPpmSchedule(TenantId tenantId, UUID clientId, UUID assetId, UUID siteId,
                                             UUID ppmScheduleId, String taskName, LocalDate dueDate) {
        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.create(tenantId, number, clientId, siteId, assetId, ppmScheduleId,
                "PPM", "NORMAL", "Planned preventive maintenance: " + taskName, "System (PPM schedule)", dueDate);
        contractTermsService.applyTerms(tenantId, wo);
        workOrderRepository.save(wo);
        log.info("FM PPM work order generated number={} schedule={} tenant={}", number, ppmScheduleId, tenantId);
        return wo;
    }

    // ── Triage ───────────────────────────────────────────────────────────────

    @Transactional
    public FmWorkOrderResponse approve(TenantId tenantId, UUID id, ApproveFmWorkRequest req) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        UUID approver = TenantContext.getCurrentUserId();
        enforceSegregation(tenantId, wo, wo.getRequestedByUserId(), approver, "approve", "REQUEST_SELF_APPROVED");

        String priority = req != null && req.priority() != null
                ? FmPriorityRules.resolve(req.priority(), wo.getCategory(), wo.isSafetyImpact()) : null;
        wo.approve(approver, priority, req != null ? req.scheduledDate() : null,
                req != null ? req.requiresVerification() : null);
        contractTermsService.reapplySla(tenantId, wo); // approval may have raised the priority
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_APPROVED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " priority=" + wo.getPriority() + " requiresVerification=" + wo.isRequiresVerification());
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse reject(TenantId tenantId, UUID id, String reason) {
        return reject(tenantId, id, reason, null);
    }

    @Transactional
    public FmWorkOrderResponse reject(TenantId tenantId, UUID id, String reason, String clientMessage) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.reject(TenantContext.getCurrentUserId(), reason, clientMessage);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_REQUEST_REJECTED", "WORK_ORDER", id, wo.getWorkOrderNumber() + " reason=" + reason);
        return toResponse(wo);
    }

    // ── Execution ────────────────────────────────────────────────────────────

    @Transactional
    public FmWorkOrderResponse assign(TenantId tenantId, UUID id, AssignFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));

        String technicianName = null;
        if (req.technicianId() != null) {
            FmTechnician t = technicianRepository.findActiveById(tenantId, req.technicianId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmTechnician", req.technicianId().toString()));
            if (!t.isActive()) {
                throw new IllegalStateException("Technician " + t.getName() + " is deactivated and cannot be assigned work");
            }
            technicianName = t.getName();
        }

        String vendorName = null;
        String overrideNote = null;
        if (req.vendorId() != null) {
            FmVendor v = vendorRepository.findActiveById(tenantId, req.vendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmVendor", req.vendorId().toString()));
            if (!v.isActive()) {
                throw new IllegalStateException("Vendor " + v.getCompanyName() + " is deactivated and cannot be assigned work");
            }
            vendorName = v.getCompanyName();
            List<String> problems = vendorEligibility.problems(tenantId, v);
            if (!problems.isEmpty()) {
                if (!accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN")) {
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
    public FmWorkOrderResponse start(TenantId tenantId, UUID id) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        Instant pausedAt = wo.getSlaPausedAt();
        wo.start();
        if (pausedAt != null) {
            contractTermsService.resumeSla(tenantId, wo, pausedAt, Instant.now());
            auditService.record(tenantId, "SLA_RESUMED", "WORK_ORDER", id,
                    wo.getWorkOrderNumber() + " totalPausedMinutes=" + wo.getSlaPausedMinutes() + " newResolutionDue=" + wo.getResolutionDueAt());
        }
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    /**
     * Putting work on hold does not stop the SLA clock unless {@code pauseSla} is set, and pausing needs the
     * SLA_MANAGE, MANAGE or ADMIN authority — so a technician cannot make a deadline disappear. Pauses are audited.
     */
    @Transactional
    public FmWorkOrderResponse putOnHold(TenantId tenantId, UUID id, HoldFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        if (req.pauseSla() && !(accessPolicy.hasAuthority("FACILITIESMANAGEMENT_SLA_MANAGE")
                || accessPolicy.hasAuthority("FACILITIESMANAGEMENT_MANAGE") || accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN"))) {
            throw new HandyFlowException("Pausing the SLA clock needs the SLA management permission",
                    HttpStatus.FORBIDDEN, "SLA_PAUSE_NOT_PERMITTED");
        }
        wo.putOnHold(req.reason(), req.pauseSla());
        workOrderRepository.save(wo);
        if (req.pauseSla()) {
            auditService.record(tenantId, "SLA_PAUSED", "WORK_ORDER", id,
                    wo.getWorkOrderNumber() + " reason=" + req.reason() + " resolutionDue=" + wo.getResolutionDueAt());
        }
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse complete(TenantId tenantId, UUID id, CompleteFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));

        if (wo.isRequiresVerification()
                && evidenceFacade.listFor(tenantId, "facilitiesmanagement", "FmWorkOrder", id).isEmpty()) {
            throw new IllegalStateException(
                    "Attach at least one photo or document as evidence before completing a job that requires verification");
        }

        // With itemised lines: the CHARGE on billable lines is what the client pays, and the COST of every line
        // is the internal cost. Without lines the supplied figure is used for both (no markup information).
        boolean hasLines = costLineRepository.countForWorkOrder(tenantId, id) > 0;
        BigDecimal cost = hasLines ? nz(costLineRepository.sumBillableChargeForWorkOrder(tenantId, id)) : req.cost();
        BigDecimal internalCost = hasLines ? nz(costLineRepository.sumCostForWorkOrder(tenantId, id)) : req.cost();
        if ("INCLUDED".equals(wo.getCoverage())) {
            cost = BigDecimal.ZERO; // covered by a retainer: recorded as internal cost only, never billed separately
        }
        if (wo.getSlaPausedAt() != null) {
            contractTermsService.resumeSla(tenantId, wo, wo.getSlaPausedAt(), Instant.now()); // completing ends any pause
        }

        wo.complete(req.completionNotes(), cost, internalCost, req.completedAt(), TenantContext.getCurrentUserId(),
                req.downtimeMinutes(), req.failureCause());
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_COMPLETED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " client=" + wo.getClientId() + " status=" + wo.getStatus() + " charge=" + cost + " internalCost=" + internalCost
                        + (hasLines ? " (billable cost lines)" : "") + " downtimeMinutes=" + req.downtimeMinutes());
        log.info("FM work order completed number={} status={} tenant={}", wo.getWorkOrderNumber(), wo.getStatus(), tenantId);

        if ("COMPLETED".equals(wo.getStatus())) advancePpmSchedule(tenantId, wo);
        if ("PENDING_VERIFICATION".equals(wo.getStatus())) notifyAwaitingVerification(tenantId, wo);
        return toResponse(wo);
    }

    // ── Verification ─────────────────────────────────────────────────────────

    @Transactional
    public FmWorkOrderResponse verify(TenantId tenantId, UUID id) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        UUID verifier = TenantContext.getCurrentUserId();
        enforceSegregation(tenantId, wo, wo.getCompletedByUserId(), verifier, "verify", "WORK_SELF_VERIFIED");

        wo.verify(verifier);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_VERIFIED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " client=" + wo.getClientId() + " cost=" + wo.getCost() + " — now billable");
        advancePpmSchedule(tenantId, wo);
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse rejectVerification(TenantId tenantId, UUID id, String reason) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.rejectVerification(reason);
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_REWORK_REQUESTED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " rework#" + wo.getReworkCount() + " reason=" + reason);
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse cancel(TenantId tenantId, UUID id, CancelFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id, accessPolicy.scope(tenantId));
        wo.cancel(req.reason());
        workOrderRepository.save(wo);
        auditService.record(tenantId, "WORK_ORDER_CANCELLED", "WORK_ORDER", id,
                wo.getWorkOrderNumber() + " reason=" + req.reason());
        return toResponse(wo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Caller may see the client; the client, its site and (optionally) an asset at that site must all exist for this tenant. */
    private void validateClientSiteAsset(TenantId tenantId, UUID clientId, UUID siteId, UUID assetId) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));
        siteRepository.findActiveByIdForClient(tenantId, clientId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", siteId.toString()));
        if (assetId != null) {
            FmAsset asset = assetRepository.findActiveById(tenantId, assetId)
                    .orElseThrow(() -> new ResourceNotFoundException("FmAsset", assetId.toString()));
            if (!asset.getSiteId().equals(siteId)) {
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
            }
        }
    }

    /**
     * The same person may not be both originator and decider. A FACILITIESMANAGEMENT_ADMIN may override;
     * that is recorded as a separate audit entry so overrides are easy to find.
     */
    private void enforceSegregation(TenantId tenantId, FmWorkOrder wo, UUID originator, UUID actor,
                                    String verb, String overrideAction) {
        if (originator == null || !originator.equals(actor)) return;
        if (accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN")) {
            auditService.record(tenantId, overrideAction, "WORK_ORDER", wo.getId(),
                    wo.getWorkOrderNumber() + " — administrator override of segregation of duties");
            return;
        }
        throw new HandyFlowException("You cannot " + verb + " your own work — another person must do this",
                HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void advancePpmSchedule(TenantId tenantId, FmWorkOrder wo) {
        if (wo.getPpmScheduleId() == null) return;
        LocalDate completedDate = wo.getCompletedAt() != null
                ? FmTime.toLocalDate(wo.getCompletedAt())
                : FmTime.today();
        ppmScheduleService.applyCompletion(tenantId, wo.getPpmScheduleId(), completedDate);
    }

    private void notifyUrgent(TenantId tenantId, FmWorkOrder wo, String titlePrefix) {
        alerts.toAdmins(tenantId, NotificationType.FM_WORKORDER_URGENT, titlePrefix + ": " + wo.getWorkOrderNumber(),
                wo.getDescription(), "/facilitiesmanagement/work-orders/" + wo.getId(), wo.getId().toString());
    }

    private void notifyRequested(TenantId tenantId, FmWorkOrder wo) {
        alerts.toAdmins(tenantId, NotificationType.FM_WORKORDER_REQUESTED, "Work request submitted: " + wo.getWorkOrderNumber(),
                "A " + wo.getPriority() + " request is waiting for approval: " + wo.getDescription(),
                "/facilitiesmanagement/work-orders/" + wo.getId(), wo.getId().toString());
    }

    private void notifyAwaitingVerification(TenantId tenantId, FmWorkOrder wo) {
        alerts.toAdmins(tenantId, NotificationType.FM_WORKORDER_AWAITING_VERIFICATION,
                "Awaiting verification: " + wo.getWorkOrderNumber(),
                wo.getWorkOrderNumber() + " has been completed and needs checking by someone other than the person who did the work.",
                "/facilitiesmanagement/work-orders/" + wo.getId(), wo.getId().toString());
    }

    FmWorkOrder findActive(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmWorkOrder wo = workOrderRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrder", id.toString()));
        if (!scope.canSeeWorkOrder(wo)) {
            throw new ResourceNotFoundException("FmWorkOrder", id.toString());
        }
        return wo;
    }

    private FmWorkOrderResponse toResponse(FmWorkOrder w) {
        return new FmWorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getClientId(), w.getSiteId(), w.getAssetId(),
                w.getPpmScheduleId(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(), w.getReportedBy(),
                w.getTechnicianId(), w.getTechnicianName(), w.getVendorId(), w.getVendorName(), w.getScheduledDate(),
                w.getCompletedAt(), w.getCompletionNotes(), w.getHoldReason(), w.getCost(), w.isInvoiced(),
                w.getCancellationReason(), w.getCreatedAt(),
                w.getRequestedByUserId(), w.isSafetyImpact(), w.getDuplicateOfWorkOrderId(), w.getApprovedAt(),
                w.getRejectionReason(), w.isRequiresVerification(), w.getVerifiedAt(),
                w.getReworkCount(), w.getLastReworkReason(), w.getDowntimeMinutes(), w.getFailureCause(),
                w.getInternalCost(),
                w.getAgreementId(), w.getCoverage(), w.getResponseDueAt(), w.getResolutionDueAt(), w.getRespondedAt(),
                FmSlaStatus.response(w, Instant.now()), FmSlaStatus.resolution(w, Instant.now()), w.getSlaPausedAt());
    }
}