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
import za.co.handyflow.platform.complianceservices.application.internal.ClientComplianceDeadlineService;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceDeadlineResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceDeadlineRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client Deadlines", description = "The compliance calendar for a client's registrations")
public class ClientComplianceDeadlineController {

    private final ClientComplianceDeadlineService deadlineService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/deadlines")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<List<ClientComplianceDeadlineResponse>>> getPendingDeadlines(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                deadlineService.getPendingDeadlines(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/clients/{clientId}/deadlines")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientComplianceDeadlineResponse>> create(
            @PathVariable UUID clientId, @Valid @RequestBody CreateClientComplianceDeadlineRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Deadline created",
                deadlineService.create(TenantContext.getTenantIdAsObject(), clientId, request,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/deadlines/{id}/mark-done")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientComplianceDeadlineResponse>> markDone(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Deadline marked done",
                deadlineService.markDone(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/deadlines/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        deadlineService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Deadline deleted", null));
    }
}
