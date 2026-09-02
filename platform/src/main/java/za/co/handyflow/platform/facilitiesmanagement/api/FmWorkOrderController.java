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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmWorkOrderService;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/work-orders")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Work Orders", description = "Job cards raised from PPM schedules or ad-hoc/reactive client requests")
public class FmWorkOrderController {

    private final FmWorkOrderService workOrderService;
    private final FmPdfService pdfService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmWorkOrderResponse>>> getWorkOrders(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                workOrderService.getWorkOrders(TenantContext.getTenantIdAsObject(), clientId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> getWorkOrder(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> createWorkOrder(@Valid @RequestBody CreateFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order created",
                workOrderService.createWorkOrder(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> assign(@PathVariable UUID id, @RequestBody AssignFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order assigned",
                workOrderService.assign(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order started",
                workOrderService.start(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> hold(@PathVariable UUID id, @RequestBody HoldFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order put on hold",
                workOrderService.putOnHold(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> complete(@PathVariable UUID id, @RequestBody CompleteFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                workOrderService.complete(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmWorkOrderResponse>> cancel(@PathVariable UUID id, @RequestBody CancelFmWorkOrderRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Work order cancelled",
                workOrderService.cancel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Evidence (before/after repair photos) ───────────────────────────────

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilitiesmanagement",
                        "FmWorkOrder", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilitiesmanagement", "FmWorkOrder", id)));
    }

    // ── Job card PDF ─────────────────────────────────────────────────────────

    @GetMapping(value = "/{id}/job-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Printable job card for the assigned technician/vendor")
    public ResponseEntity<byte[]> downloadJobCard(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        byte[] pdf = pdfService.generateJobCard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-card.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
