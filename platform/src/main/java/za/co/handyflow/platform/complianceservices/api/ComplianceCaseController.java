package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceCaseService;
import za.co.handyflow.platform.complianceservices.dto.CaseDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}/cases")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Cases", description = "Putting right the problems found for a client")
public class ComplianceCaseController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceCaseService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<CaseSummary>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CaseDetail>> get(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), clientId, id)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> open(@PathVariable UUID clientId, @Valid @RequestBody OpenRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Case opened",
                service.open(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> transition(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.transition(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}/owner")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> owner(@PathVariable UUID clientId, @PathVariable UUID id, @RequestBody OwnerRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.assign(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}/due")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> due(@PathVariable UUID clientId, @PathVariable UUID id, @RequestBody DueRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.reschedule(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/{id}/steps")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> addStep(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody StepRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.addStep(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/{id}/steps/{stepId}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> completeStep(@PathVariable UUID clientId, @PathVariable UUID id, @PathVariable UUID stepId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.completeStep(TenantContext.getTenantIdAsObject(), clientId, id, stepId, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/steps/{stepId}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CaseDetail>> cancelStep(@PathVariable UUID clientId, @PathVariable UUID id, @PathVariable UUID stepId, @RequestBody StepCancelRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.cancelStep(TenantContext.getTenantIdAsObject(), clientId, id, stepId, request, TenantContext.getCurrentUserId())));
    }
}
