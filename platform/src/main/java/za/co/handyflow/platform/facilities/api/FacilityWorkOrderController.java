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
import za.co.handyflow.platform.facilities.application.internal.FacilityWorkOrderService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/work-orders")
@RequiredArgsConstructor
@Tag(name = "Facilities - Work Orders", description = "Job cards raised from PPM schedules or ad-hoc/reactive requests")
public class FacilityWorkOrderController {

    private final FacilityWorkOrderService workOrderService;
    private final FacilityPdfService pdfService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<WorkOrderResponse>>> getWorkOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                workOrderService.getWorkOrders(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> getWorkOrder(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order created",
                workOrderService.createWorkOrder(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> assign(@PathVariable UUID id, @RequestBody AssignWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order assigned",
                workOrderService.assign(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order started",
                workOrderService.start(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> hold(@PathVariable UUID id, @RequestBody HoldWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order put on hold",
                workOrderService.putOnHold(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> complete(@PathVariable UUID id, @RequestBody CompleteWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                workOrderService.complete(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> cancel(@PathVariable UUID id, @RequestBody CancelWorkOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order cancelled",
                workOrderService.cancel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Evidence (before/after repair photos) ───────────────────────────────

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilities",
                        "FacilityWorkOrder", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilities", "FacilityWorkOrder", id)));
    }

    // ── Job card PDF ─────────────────────────────────────────────────────────

    @GetMapping(value = "/{id}/job-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    @Operation(summary = "Printable job card for the assigned technician/vendor")
    public ResponseEntity<byte[]> downloadJobCard(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        byte[] pdf = pdfService.generateJobCard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-card.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
