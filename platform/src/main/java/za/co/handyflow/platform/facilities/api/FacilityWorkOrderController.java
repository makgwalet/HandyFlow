package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.facilities.application.internal.FacilityPdfService;
import za.co.handyflow.platform.facilities.application.internal.FacilityWorkOrderCostService;
import za.co.handyflow.platform.facilities.application.internal.FacilityWorkOrderService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Authorities (MANAGE and ADMIN keep full access everywhere):
 * <ul>
 *   <li>raise a request — FACILITIES_WORKORDER_REQUEST (anyone who reports faults; sees only own requests)</li>
 *   <li>create directly — FACILITIES_WORKORDER_CREATE</li>
 *   <li>approve / reject a request — FACILITIES_WORKORDER_APPROVE</li>
 *   <li>assign, cancel — FACILITIES_WORKORDER_ASSIGN</li>
 *   <li>start, hold, complete, costs, evidence — FACILITIES_TECHNICIAN (own jobs only)</li>
 *   <li>verify / send back for rework — FACILITIES_WORKORDER_VERIFY</li>
 * </ul>
 * Evidence, cost and job-card endpoints check the caller may see the work order first.
 */
@RestController
@RequestMapping("/api/v1/facilities/work-orders")
@RequiredArgsConstructor
@Tag(name = "Facilities - Work Orders", description = "Requests and job cards: intake, approval, execution, verification and cost capture")
public class FacilityWorkOrderController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN'," +
            "'FACILITIES_TECHNICIAN','FACILITIES_WORKORDER_REQUEST','FACILITIES_WORKORDER_CREATE'," +
            "'FACILITIES_WORKORDER_ASSIGN','FACILITIES_WORKORDER_APPROVE','FACILITIES_WORKORDER_VERIFY')";
    private static final String CAN_APPROVE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_APPROVE')";
    private static final String CAN_VERIFY = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_VERIFY')";
    private static final String CAN_EXECUTE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN')";

    private final FacilityWorkOrderService workOrderService;
    private final FacilityWorkOrderCostService costService;
    private final FacilityPdfService pdfService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<WorkOrderResponse>>> getWorkOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                workOrderService.getWorkOrders(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<WorkOrderResponse>> getWorkOrder(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Intake & triage ──────────────────────────────────────────────────────

    @PostMapping("/requests")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_CREATE','FACILITIES_WORKORDER_REQUEST')")
    @Operation(summary = "Raise a work request (starts REQUESTED; needs approval before it can be assigned)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> raiseRequest(@Valid @RequestBody RaiseWorkRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request raised",
                workOrderService.raiseRequest(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve a request (cannot approve your own, unless FACILITIES_ADMIN — audited)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> approve(
            @PathVariable UUID id, @RequestBody(required = false) ApproveWorkRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Request approved",
                workOrderService.approve(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<WorkOrderResponse>> reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Request rejected",
                workOrderService.reject(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_CREATE')")
    @Operation(summary = "Create a work order directly (starts OPEN, no approval step)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order created",
                workOrderService.createWorkOrder(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Execution ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_ASSIGN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> assign(@PathVariable UUID id, @RequestBody AssignWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order assigned",
                workOrderService.assign(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<WorkOrderResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order started",
                workOrderService.start(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<WorkOrderResponse>> hold(@PathVariable UUID id, @RequestBody HoldWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order put on hold",
                workOrderService.putOnHold(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_EXECUTE)
    @Operation(summary = "Complete work. If the work order requires verification it moves to PENDING_VERIFICATION and needs evidence attached first.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> complete(@PathVariable UUID id, @RequestBody CompleteWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                workOrderService.complete(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Verification ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/verify")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Verify completed work (cannot verify your own completion, unless FACILITIES_ADMIN — audited)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> verify(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order verified",
                workOrderService.verify(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reject-verification")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Send completed work back for rework (returns to IN_PROGRESS)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> rejectVerification(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work sent back for rework",
                workOrderService.rejectVerification(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_WORKORDER_ASSIGN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> cancel(@PathVariable UUID id, @RequestBody CancelWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order cancelled",
                workOrderService.cancel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Cost lines ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/costs")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<CostLineResponse>>> getCosts(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(costService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/costs")
    @PreAuthorize(CAN_EXECUTE)
    @Operation(summary = "Add an itemised cost line (labour, parts, travel, contractor, other)")
    public ResponseEntity<ApiResponse<CostLineResponse>> addCost(@PathVariable UUID id, @Valid @RequestBody AddCostLineRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Cost line added",
                costService.add(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}/costs/{lineId}")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<Void>> removeCost(@PathVariable UUID id, @PathVariable UUID lineId) {
        featureGuard.requireModule("facilities");
        costService.remove(TenantContext.getTenantIdAsObject(), id, lineId);
        return ResponseEntity.ok(ApiResponse.success("Cost line removed", null));
    }

    // ── Evidence (photos) ────────────────────────────────────────────────────

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN','FACILITIES_WORKORDER_REQUEST')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilities");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilities",
                        "FacilityWorkOrder", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilities", "FacilityWorkOrder", id)));
    }

    // ── Job card PDF ─────────────────────────────────────────────────────────

    @GetMapping(value = "/{id}/job-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Printable job card for the assigned technician/vendor")
    public ResponseEntity<byte[]> downloadJobCard(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        byte[] pdf = pdfService.generateJobCard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-card.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}