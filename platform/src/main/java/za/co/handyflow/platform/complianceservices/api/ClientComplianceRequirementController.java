package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ClientComplianceRequirementService;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceRequirementResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRequirementRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRequirementRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * No DELETE endpoint — deliberately, same reason as
 * compliancetender.ComplianceRequirementController: see
 * ClientComplianceRequirement's own Javadoc.
 */
@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client Requirements", description = "Versioned requirement definitions tracked for a specific client")
public class ClientComplianceRequirementController {

    private final ClientComplianceRequirementService requirementService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "The current version of every requirement for this client — one row per code")
    public ResponseEntity<ApiResponse<List<ClientComplianceRequirementResponse>>> getRequirements(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirements(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/requirements/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientComplianceRequirementResponse>> getRequirement(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirement(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Define a new requirement (version 1) for this client")
    public ResponseEntity<ApiResponse<ClientComplianceRequirementResponse>> create(
            @PathVariable UUID clientId, @Valid @RequestBody CreateClientComplianceRequirementRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement created",
                requirementService.create(TenantContext.getTenantIdAsObject(), clientId, request,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/requirements/{id}/new-version")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Create a new version of this requirement — the existing version is left untouched")
    public ResponseEntity<ApiResponse<ClientComplianceRequirementResponse>> createNewVersion(
            @PathVariable UUID id, @Valid @RequestBody UpdateClientComplianceRequirementRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("New version created",
                requirementService.createNewVersion(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }
}
