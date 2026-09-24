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
import za.co.handyflow.platform.complianceservices.application.internal.ClientComplianceRegistrationService;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceRegistrationResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client Registrations", description = "CIPC/SARS/PSIRA/etc. registrations tracked on a client's behalf")
public class ClientComplianceRegistrationController {

    private final ClientComplianceRegistrationService registrationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/registrations")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<List<ClientComplianceRegistrationResponse>>> getRegistrations(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                registrationService.getRegistrations(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/registrations/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientComplianceRegistrationResponse>> getRegistration(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(
                registrationService.getRegistration(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/registrations")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Record a compliance registration tracked on this client's behalf")
    public ResponseEntity<ApiResponse<ClientComplianceRegistrationResponse>> create(
            @PathVariable UUID clientId, @Valid @RequestBody CreateClientComplianceRegistrationRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration recorded",
                registrationService.create(TenantContext.getTenantIdAsObject(), clientId, request,
                        TenantContext.getCurrentUserId())));
    }

    @PutMapping("/registrations/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ClientComplianceRegistrationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateClientComplianceRegistrationRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Registration updated",
                registrationService.update(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/registrations/{id}")
    @PreAuthorize("hasAuthority('COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        registrationService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Registration deleted", null));
    }
}
