package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPdfService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmWorkOrderCostService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmWorkOrderService;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Authority split (MANAGE and ADMIN keep full access everywhere):
 * create — FACILITIESMANAGEMENT_WORKORDER_CREATE; assign, cancel — FACILITIESMANAGEMENT_WORKORDER_ASSIGN;
 * start, hold, complete, attach evidence — FACILITIESMANAGEMENT_TECHNICIAN (own jobs only).
 * Evidence and job-card endpoints verify the caller may see the work order first; previously they accepted
 * any work order id in the tenant.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/work-orders")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Work Orders", description = "Job cards raised from PPM schedules or ad-hoc/reactive client requests")
public class FmWorkOrderController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE'," +
            "'FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_TECHNICIAN','FACILITIESMANAGEMENT_WORKORDER_CREATE'," +
            "'FACILITIESMANAGEMENT_WORKORDER_ASSIGN')";
    private static final String CAN_TRIAGE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN'," +
            "'FACILITIESMANAGEMENT_WORKORDER_ASSIGN')";
    private static final String CAN_EXECUTE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_TECHNICIAN')";

    private final FmWorkOrderService workOrderService;
    private final FmPdfService pdfService;
    private final FmWorkOrderCostService costService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<FmWorkOrderResponse>>> getWorkOrders(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                workOrderService.getWorkOrders(TenantContext.getTenantIdAsObject(), clientId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> getWorkOrder(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_WORKORDER_CREATE')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> createWorkOrder(@Valid @RequestBody CreateFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order created",
                workOrderService.createWorkOrder(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_WORKORDER_CREATE')")
    @Operation(summary = "Log a client's fault report (starts REQUESTED; must be approved before it can be assigned)")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> raiseRequest(@Valid @RequestBody RaiseFmWorkRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request raised",
                workOrderService.raiseRequest(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_TRIAGE)
    @Operation(summary = "Approve a request (you cannot approve your own unless ADMIN — audited)")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> approve(
            @PathVariable UUID id, @RequestBody(required = false) ApproveFmWorkRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Request approved",
                workOrderService.approve(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_TRIAGE)
    @Operation(summary = "Decline a request. 'reason' is internal; 'clientMessage' is shown to the client in the portal")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> reject(@PathVariable UUID id, @Valid @RequestBody FmRejectRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Request declined",
                workOrderService.reject(TenantContext.getTenantIdAsObject(), id, request.reason(), request.clientMessage())));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(CAN_TRIAGE)
    @Operation(summary = "Verify completed work so it becomes billable (the person who completed it cannot verify it unless ADMIN — audited)")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> verify(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work verified",
                workOrderService.verify(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/rework")
    @PreAuthorize(CAN_TRIAGE)
    @Operation(summary = "Send completed work back for rework")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> rework(@PathVariable UUID id, @Valid @RequestBody FmReasonRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work sent back for rework",
                workOrderService.rejectVerification(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_WORKORDER_ASSIGN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> assign(@PathVariable UUID id, @RequestBody AssignFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order assigned",
                workOrderService.assign(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order started",
                workOrderService.start(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> hold(@PathVariable UUID id, @RequestBody HoldFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order put on hold",
                workOrderService.putOnHold(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> complete(@PathVariable UUID id, @RequestBody CompleteFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                workOrderService.complete(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_WORKORDER_ASSIGN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> cancel(@PathVariable UUID id, @RequestBody CancelFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order cancelled",
                workOrderService.cancel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Evidence (before/after repair photos) ───────────────────────────────

    // ── cost lines ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/cost-lines")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<FmCostLineResponse>>> costLines(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(costService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/cost-lines")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<FmCostLineResponse>> addCostLine(@PathVariable UUID id, @Valid @RequestBody AddFmCostLineRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Cost line added",
                costService.add(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}/cost-lines/{lineId}")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<Void>> removeCostLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        featureGuard.requireModule("facilitiesmanagement");
        costService.remove(TenantContext.getTenantIdAsObject(), id, lineId);
        return ResponseEntity.ok(ApiResponse.success("Cost line removed", null));
    }

    @PostMapping("/{id}/evidence")
    @PreAuthorize(CAN_EXECUTE)
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilitiesmanagement");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilitiesmanagement",
                        "FmWorkOrder", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilitiesmanagement", "FmWorkOrder", id)));
    }

    // ── Job card PDF ─────────────────────────────────────────────────────────

    @GetMapping(value = "/{id}/job-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Printable job card for the assigned technician/vendor")
    public ResponseEntity<byte[]> downloadJobCard(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        workOrderService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        byte[] pdf = pdfService.generateJobCard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-card.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}