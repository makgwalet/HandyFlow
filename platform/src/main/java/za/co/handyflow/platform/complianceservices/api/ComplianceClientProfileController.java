package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceClientProfileService;
import za.co.handyflow.platform.complianceservices.dto.ClientProfileDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}/profile")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client profile", description = "The client's registered details and where each came from")
public class ComplianceClientProfileController {

    private final ComplianceClientProfileService profileService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> get(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(profileService.get(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> save(@PathVariable UUID clientId, @Valid @RequestBody SaveProfileRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                profileService.save(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/sources/{field}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> saveSource(@PathVariable UUID clientId, @PathVariable String field,
                                                                    @Valid @RequestBody SaveSourceRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Source recorded",
                profileService.saveSource(TenantContext.getTenantIdAsObject(), clientId, field, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/sources/{field}/confirm")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> confirm(@PathVariable UUID clientId, @PathVariable String field,
                                                                 @Valid @RequestBody(required = false) ConfirmSourceRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Confirmed",
                profileService.confirm(TenantContext.getTenantIdAsObject(), clientId, field,
                        request == null ? null : request.exceptionReason(), TenantContext.getCurrentUserId())));
    }
}
