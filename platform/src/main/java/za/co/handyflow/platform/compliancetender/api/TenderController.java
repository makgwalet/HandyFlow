package za.co.handyflow.platform.compliancetender.api;

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
import za.co.handyflow.platform.compliancetender.application.internal.TenderService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPersonnelService;
import za.co.handyflow.platform.compliancetender.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/tenders")
@RequiredArgsConstructor
@Tag(name = "Compliance - Tenders", description = "Tender workspace: opportunities, requirement matrix, lifecycle, outcome")
public class TenderController {

    private final TenderService tenderService;
    private final TenderPersonnelService personnelService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<TenderResponse>>> getTenders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTenders(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderResponse>> getTender(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTender(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Create a new tender — assigns this tenant's own tender number")
    public ResponseEntity<ApiResponse<TenderResponse>> create(@Valid @RequestBody CreateTenderRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tender created",
                tenderService.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Move a tender to the next lifecycle status (DRAFT -> ... -> SUBMITTED -> ...)")
    public ResponseEntity<ApiResponse<TenderResponse>> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionTenderRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Tender status updated",
                tenderService.transition(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/outcome")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Record AWARDED or UNSUCCESSFUL, with reason and (if awarded) the awarded value")
    public ResponseEntity<ApiResponse<TenderResponse>> recordOutcome(
            @PathVariable UUID id, @Valid @RequestBody RecordTenderOutcomeRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Tender outcome recorded",
                tenderService.recordOutcome(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    // ── Requirement matrix ──────────────────────────────────────────────────

    @GetMapping("/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<TenderRequirementResponse>>> getRequirements(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getRequirements(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderRequirementResponse>> addRequirement(
            @PathVariable UUID id, @Valid @RequestBody CreateTenderRequirementRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement added",
                tenderService.addRequirement(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/requirements/{requirementId}/status")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderRequirementResponse>> updateRequirementStatus(
            @PathVariable UUID requirementId, @Valid @RequestBody UpdateTenderRequirementStatusRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Requirement status updated",
                tenderService.updateRequirementStatus(TenantContext.getTenantIdAsObject(), requirementId, request,
                        TenantContext.getCurrentUserId())));
    }

    // ── Personnel — referenced from HR, not copied ──────────────────────────

    @GetMapping("/{id}/personnel")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Key personnel for this tender — names/details are looked up live from HR, never duplicated")
    public ResponseEntity<ApiResponse<List<TenderPersonnelResponse>>> getPersonnel(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                personnelService.getPersonnel(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/personnel")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Reference an existing HR employee as key personnel for this tender")
    public ResponseEntity<ApiResponse<TenderPersonnelResponse>> addPersonnel(
            @PathVariable UUID id, @Valid @RequestBody AddTenderPersonnelRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Personnel added",
                personnelService.addPersonnel(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/personnel/{personnelId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removePersonnel(@PathVariable UUID personnelId) {
        featureGuard.requireModule("compliancetender");
        personnelService.removePersonnel(TenantContext.getTenantIdAsObject(), personnelId);
        return ResponseEntity.ok(ApiResponse.success("Personnel removed", null));
    }
}
