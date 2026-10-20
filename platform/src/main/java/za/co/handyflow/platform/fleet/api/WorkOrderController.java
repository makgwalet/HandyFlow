package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.WorkOrderService;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderAttachmentType;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderStatus;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Workshop work orders. FLEET_READ to view; FLEET_MANAGE to raise, quote, run and complete work;
 * FLEET_ADMIN to approve or reject a quotation (spend authority) and to delete attachments.
 */
@RestController
@RequestMapping("/api/v1/fleet/work-orders")
@RequiredArgsConstructor
@Tag(name = "Fleet - Workshop", description = "Work orders: quotation, approval, labour and parts, completion, downtime")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final FeatureGuard featureGuard;

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List work orders, newest first; filter by vehicleId and/or status")
    public ResponseEntity<ApiResponse<Page<WorkOrderResponse>>> list(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) WorkOrderStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                workOrderService.list(TenantContext.getTenantIdAsObject(), vehicleId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "A work order with its quotation, approval, cost lines, totals, variance to quote, downtime and attachments")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Create / edit ─────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Raise a work order (optionally from a fleet issue and/or accident). Reference like WO-2026-00042")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order created",
                workOrderService.create(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Edit the work order's details (until it is COMPLETED or CANCELLED)")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateWorkOrderRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work order updated",
                workOrderService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Quotation & approval ──────────────────────────────────────────────────

    @PutMapping("/{id}/quotation")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record (or revise) the supplier's quotation; moves the order to QUOTED and notifies admins for approval")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> recordQuotation(
            @PathVariable UUID id, @Valid @RequestBody RecordQuotationRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Quotation recorded",
                workOrderService.recordQuotation(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Approve the quotation (QUOTED → APPROVED). An expired quotation cannot be approved")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) WorkOrderNoteRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work order approved",
                workOrderService.approve(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId(),
                        request != null ? request.note() : null)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Reject the quotation (QUOTED → OPEN). It must be revised, or the order cancelled")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderReasonRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Quotation rejected",
                workOrderService.reject(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    // ── Work ──────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Start work. Needs approval first if there is a quotation. Moves the vehicle to MAINTENANCE and the linked issue to IN_PROGRESS")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work started",
                workOrderService.start(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Put work on hold (e.g. waiting for parts). The vehicle stays in MAINTENANCE and downtime keeps counting")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> hold(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderReasonRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work order on hold",
                workOrderService.hold(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work resumed",
                workOrderService.resume(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Complete the order. Writes a service-history record with the actual total (feeding cost-per-km), "
            + "resets the service interval for SERVICE orders, resolves the linked issue, and returns the vehicle to AVAILABLE")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteWorkOrderRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                workOrderService.complete(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> cancel(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderReasonRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Work order cancelled",
                workOrderService.cancel(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request.reason())));
    }

    // ── Cost lines ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Log an actual cost line (labour hours, part, sublet, other) while the work is IN_PROGRESS or ON_HOLD")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> addLine(
            @PathVariable UUID id, @Valid @RequestBody SaveWorkOrderLineRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Line added",
                workOrderService.addLine(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> updateLine(
            @PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody SaveWorkOrderLineRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Line updated",
                workOrderService.updateLine(TenantContext.getTenantIdAsObject(), id, lineId, request)));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<WorkOrderDetailResponse>> removeLine(
            @PathVariable UUID id, @PathVariable UUID lineId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Line removed",
                workOrderService.removeLine(TenantContext.getTenantIdAsObject(), id, lineId)));
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Attach a quotation, invoice, job card or photo (PDF, image, DOCX; max 15 MB) in any status")
    public ResponseEntity<ApiResponse<WorkOrderAttachmentResponse>> uploadAttachment(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam WorkOrderAttachmentType type,
            @RequestParam(required = false) String description) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attachment added",
                workOrderService.uploadAttachment(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), file, type, description)));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        featureGuard.requireModule("fleet");
        WorkOrderService.AttachmentDownload dl =
                workOrderService.downloadAttachment(TenantContext.getTenantIdAsObject(), id, attachmentId);
        // Always an attachment + nosniff: uploaded content is never rendered inline in our origin.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Delete an attachment (soft delete — the stored file is retained)")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        featureGuard.requireModule("fleet");
        workOrderService.deleteAttachment(TenantContext.getTenantIdAsObject(), id, attachmentId,
                UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted", null));
    }
}