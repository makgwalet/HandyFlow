package za.co.handyflow.platform.complianceservices.api;

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
import za.co.handyflow.platform.complianceservices.application.internal.ClientTenderService;
import za.co.handyflow.platform.complianceservices.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client Tenders", description = "Tender workspace for a specific client — lifecycle, requirement matrix")
public class ClientTenderController {

    private final ClientTenderService tenderService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/tenders")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ClientTenderResponse>>> getTenders(
            @PathVariable UUID clientId, @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTenders(TenantContext.getTenantIdAsObject(), clientId, status, pageable)));
    }

    @GetMapping("/tenders/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientTenderResponse>> getTender(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTender(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/tenders")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Create a new tender for this client — assigns a CTND-prefixed tender number")
    public ResponseEntity<ApiResponse<ClientTenderResponse>> create(
            @PathVariable UUID clientId, @Valid @RequestBody CreateClientTenderRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tender created",
                tenderService.create(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/tenders/{id}/transition")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientTenderResponse>> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionClientTenderRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Tender status updated",
                tenderService.transition(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/tenders/{id}/outcome")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientTenderResponse>> recordOutcome(
            @PathVariable UUID id, @Valid @RequestBody RecordClientTenderOutcomeRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Tender outcome recorded",
                tenderService.recordOutcome(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/tenders/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<List<ClientTenderRequirementResponse>>> getRequirements(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getRequirements(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/tenders/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientTenderRequirementResponse>> addRequirement(
            @PathVariable UUID id, @Valid @RequestBody CreateClientTenderRequirementRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement added",
                tenderService.addRequirement(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/tenders/requirements/{requirementId}/status")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientTenderRequirementResponse>> updateRequirementStatus(
            @PathVariable UUID requirementId, @Valid @RequestBody UpdateClientTenderRequirementStatusRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Requirement status updated",
                tenderService.updateRequirementStatus(TenantContext.getTenantIdAsObject(), requirementId, request,
                        TenantContext.getCurrentUserId())));
    }
}
