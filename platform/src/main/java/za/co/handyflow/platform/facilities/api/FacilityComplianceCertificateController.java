package za.co.handyflow.platform.facilities.api;

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
import za.co.handyflow.platform.facilities.application.internal.FacilityComplianceCertificateService;
import za.co.handyflow.platform.facilities.dto.ComplianceCertificateResponse;
import za.co.handyflow.platform.facilities.dto.CreateComplianceCertificateRequest;
import za.co.handyflow.platform.facilities.dto.RevokeCertificateRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/compliance")
@RequiredArgsConstructor
@Tag(name = "Facilities - Compliance", description = "Electrical COC, fire equipment, elevator and gas compliance certificates")
public class FacilityComplianceCertificateController {

    private final FacilityComplianceCertificateService certificateService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ComplianceCertificateResponse>>> getCertificates(
            @RequestParam(required = false) UUID siteId,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                certificateService.getCertificates(TenantContext.getTenantIdAsObject(), siteId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceCertificateResponse>> getCertificate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(certificateService.getCertificate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceCertificateResponse>> issue(@Valid @RequestBody CreateComplianceCertificateRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Certificate issued",
                certificateService.issue(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    @Operation(summary = "Revoke a certificate — ADMIN-only, matching this module's own equivalent of every " +
            "other module's issue/revoke gating on its own certificate-shaped entity (Training's TrainingCertificate, etc.)")
    public ResponseEntity<ApiResponse<ComplianceCertificateResponse>> revoke(
            @PathVariable UUID id, @Valid @RequestBody RevokeCertificateRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Certificate revoked",
                certificateService.revoke(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }
}
