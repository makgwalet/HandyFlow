package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ClientObligationService;
import za.co.handyflow.platform.complianceservices.dto.ClientObligationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}/obligations")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client obligations", description = "Which catalogue obligations apply to a client, and why")
public class ClientObligationController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ClientObligationService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AssignmentsResponse>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/refresh")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentsResponse>> refresh(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Suggestions updated",
                service.refresh(TenantContext.getTenantIdAsObject(), clientId, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PutMapping("/{obligationId}/decision")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentsResponse>> decide(@PathVariable UUID clientId, @PathVariable UUID obligationId,
                                                                    @Valid @RequestBody DecisionRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded",
                service.decide(TenantContext.getTenantIdAsObject(), clientId, obligationId, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/{obligationId}/approve")
    @PreAuthorize("hasAuthority('COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentsResponse>> approve(@PathVariable UUID clientId, @PathVariable UUID obligationId,
                                                                     @Valid @RequestBody(required = false) ApproveDecisionRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Approved",
                service.approve(TenantContext.getTenantIdAsObject(), clientId, obligationId,
                        request == null ? null : request.exceptionReason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/{obligationId}/history")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<HistoryView>>> history(@PathVariable UUID clientId, @PathVariable UUID obligationId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.history(TenantContext.getTenantIdAsObject(), clientId, obligationId)));
    }
}
