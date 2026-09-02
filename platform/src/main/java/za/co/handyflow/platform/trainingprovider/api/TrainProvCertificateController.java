package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCertificateService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPdfService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCertificate;
import za.co.handyflow.platform.trainingprovider.dto.CancelRequest;
import za.co.handyflow.platform.trainingprovider.dto.CertificateResponse;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Certificates", description = "Certificates issued for completed, certification-eligible enrollments")
public class TrainProvCertificateController {

    private final TrainProvCertificateService certificateService;
    private final TrainProvPdfService pdfService;
    private final JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping("/certificates")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CertificateResponse>>> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(certificateService
                .list(TenantContext.getTenantIdAsObject(), clientId, status, pageable)
                .map(this::toResponse)));
    }

    @GetMapping("/certificates/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CertificateResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(certificateService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{enrollmentId}/certificate")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Issue an accredited certificate for a completed enrollment — ADMIN only")
    public ResponseEntity<ApiResponse<CertificateResponse>> issue(@PathVariable UUID enrollmentId) {
        featureGuard.requireModule("trainingprovider");
        TrainProvCertificate certificate = certificateService.issue(TenantContext.getTenantIdAsObject(), enrollmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Certificate issued", toResponse(certificate)));
    }

    @PostMapping("/certificates/{id}/revoke")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Revoke a previously issued certificate — ADMIN only")
    public ResponseEntity<ApiResponse<CertificateResponse>> revoke(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("trainingprovider");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(certificateService.revoke(TenantContext.getTenantIdAsObject(), id, reason))));
    }

    @GetMapping("/certificates/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<byte[]> downloadCertificatePdf(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvCertificate certificate = certificateService.get(tenantId, id);
        byte[] pdf = pdfService.generateCertificate(certificate, fetchProviderName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + certificate.getCertificateNumber() + ".pdf\"")
                .body(pdf);
    }

    private String fetchProviderName(TenantId tenantId) {
        try {
            var row = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue());
            Object name = row.get("name");
            return name != null ? name.toString() : "HandyFlow Training Provider";
        } catch (Exception e) {
            log.warn("Could not fetch tenant name for certificate PDF: {}", e.getMessage());
            return "HandyFlow Training Provider";
        }
    }

    private CertificateResponse toResponse(TrainProvCertificate c) {
        return new CertificateResponse(c.getId(), c.getEnrollmentId(), c.getDelegateId(), c.getClientId(),
                c.getDelegateNameSnapshot(), c.getClientNameSnapshot(), c.getCourseTitleSnapshot(),
                c.getUnitStandardSnapshot(), c.getCertificateNumber(), c.getIssueDate(), c.getExpiryDate(),
                c.getStatus(), c.getRevokedReason(), c.getCreatedAt());
    }
}
