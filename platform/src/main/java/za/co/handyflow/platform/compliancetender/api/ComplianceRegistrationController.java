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
import za.co.handyflow.platform.compliancetender.application.internal.ComplianceRegistrationService;
import za.co.handyflow.platform.compliancetender.dto.ComplianceRegistrationResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRegistrationRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/registrations")
@RequiredArgsConstructor
@Tag(name = "Compliance - Registrations", description = "CIPC, SARS, UIF, PSIRA, CSD, cidb and NHBRC registration tracking")
public class ComplianceRegistrationController {

    private final ComplianceRegistrationService registrationService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ComplianceRegistrationResponse>>> getRegistrations(
            @RequestParam(required = false) String authority,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                registrationService.getRegistrations(TenantContext.getTenantIdAsObject(), authority, pageable)));
    }

    /** Every registration, unpaginated — for the "My Business Compliance" summary view. */
    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplianceRegistrationResponse>>> getAllRegistrations() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                registrationService.getAllRegistrations(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceRegistrationResponse>> getRegistration(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                registrationService.getRegistration(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Record a new compliance registration for the tenant's own business")
    public ResponseEntity<ApiResponse<ComplianceRegistrationResponse>> create(
            @Valid @RequestBody CreateComplianceRegistrationRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration recorded",
                registrationService.create(TenantContext.getTenantIdAsObject(), request,
                        TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceRegistrationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateComplianceRegistrationRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Registration updated",
                registrationService.update(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        registrationService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Registration deleted", null));
    }
}
