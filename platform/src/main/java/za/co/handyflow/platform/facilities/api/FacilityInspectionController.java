package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityInspectionService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Templates are managed by MANAGE/ADMIN/COMPLIANCE_MANAGE; running an inspection needs FACILITIES_INSPECT (or
 * MANAGE/ADMIN/TECHNICIAN). Everyone who can read facilities can read templates and results.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities - Inspections", description = "Inspection checklists, inspection runs and their results")
public class FacilityInspectionController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN'," +
            "'FACILITIES_TECHNICIAN','FACILITIES_INSPECT','FACILITIES_COMPLIANCE_MANAGE')";
    private static final String CAN_MANAGE_TEMPLATES = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";
    private static final String CAN_INSPECT = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN','FACILITIES_INSPECT')";

    private final FacilityInspectionService service;
    private final FeatureGuard featureGuard;

    // ── Templates ────────────────────────────────────────────────────────────

    @GetMapping("/inspection-templates")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<InspectionTemplateResponse>>> listTemplates(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.listTemplates(TenantContext.getTenantIdAsObject(), includeInactive)));
    }

    @GetMapping("/inspection-templates/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> getTemplate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.getTemplate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/inspection-templates")
    @PreAuthorize(CAN_MANAGE_TEMPLATES)
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> createTemplate(@Valid @RequestBody InspectionTemplateRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template created",
                service.createTemplate(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/inspection-templates/{id}")
    @PreAuthorize(CAN_MANAGE_TEMPLATES)
    @Operation(summary = "Replace a template's checks. Inspections already started keep the checks they were started with")
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody InspectionTemplateRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Template updated",
                service.updateTemplate(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inspection-templates/{id}/activate")
    @PreAuthorize(CAN_MANAGE_TEMPLATES)
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.setTemplateActive(TenantContext.getTenantIdAsObject(), id, true)));
    }

    @PostMapping("/inspection-templates/{id}/deactivate")
    @PreAuthorize(CAN_MANAGE_TEMPLATES)
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.setTemplateActive(TenantContext.getTenantIdAsObject(), id, false)));
    }

    // ── Inspections ──────────────────────────────────────────────────────────

    @GetMapping("/inspections")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<InspectionResponse>>> list(
            @RequestParam(required = false) UUID siteId, @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) String status, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, assetId, status, pageable)));
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<InspectionResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/inspections")
    @PreAuthorize(CAN_INSPECT)
    public ResponseEntity<ApiResponse<InspectionResponse>> start(@Valid @RequestBody StartInspectionRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inspection started",
                service.start(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/inspections/{id}/answers")
    @PreAuthorize(CAN_INSPECT)
    @Operation(summary = "Save one or more answers (PASS, FAIL, NA). A FAIL needs a comment. Resending an answer replaces it")
    public ResponseEntity<ApiResponse<InspectionResponse>> answer(@PathVariable UUID id, @Valid @RequestBody InspectionAnswersRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.answer(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inspections/{id}/complete")
    @PreAuthorize(CAN_INSPECT)
    @Operation(summary = "Complete the inspection. Every failed check raises a corrective action")
    public ResponseEntity<ApiResponse<InspectionResponse>> complete(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CompleteInspectionRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Inspection completed",
                service.complete(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inspections/{id}/cancel")
    @PreAuthorize(CAN_INSPECT)
    public ResponseEntity<ApiResponse<InspectionResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Inspection cancelled",
                service.cancel(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }
}
