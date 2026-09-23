package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.ComplianceRequirementService;
import za.co.handyflow.platform.compliancetender.dto.ComplianceRequirementResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRequirementRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRequirementRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * No DELETE endpoint here — deliberately. See
 * ComplianceRequirementService's own Javadoc: this entity is versioned
 * specifically so a past readiness check stays judged against the rules
 * that were actually in force, and deleting a version would break that
 * guarantee for anything that referenced it.
 */
@RestController
@RequestMapping("/api/v1/compliance/requirements")
@RequiredArgsConstructor
@Tag(name = "Compliance - Requirements", description = "Versioned, reusable requirement definitions (e.g. CSD_ACTIVE, CIDB_GRADE)")
public class ComplianceRequirementController {

    private final ComplianceRequirementService requirementService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "The current version of every requirement — one row per code")
    public ResponseEntity<ApiResponse<List<ComplianceRequirementResponse>>> getRequirements() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirements(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceRequirementResponse>> getRequirement(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirement(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/code/{code}/history")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Every version ever recorded for this code, most recent first — the audit trail")
    public ResponseEntity<ApiResponse<List<ComplianceRequirementResponse>>> getRequirementHistory(@PathVariable String code) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirementHistory(TenantContext.getTenantIdAsObject(), code)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Define a new requirement (version 1) — fails if this code already exists, use the new-version endpoint instead")
    public ResponseEntity<ApiResponse<ComplianceRequirementResponse>> create(
            @Valid @RequestBody CreateComplianceRequirementRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement created",
                requirementService.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Create a new version of this requirement — the existing version is left untouched, never edited in place")
    public ResponseEntity<ApiResponse<ComplianceRequirementResponse>> createNewVersion(
            @PathVariable UUID id, @Valid @RequestBody UpdateComplianceRequirementRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("New version created",
                requirementService.createNewVersion(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }
}
