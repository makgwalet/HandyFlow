package za.co.handyflow.platform.training.api;

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
import za.co.handyflow.platform.training.application.internal.TrainingCertificateService;
import za.co.handyflow.platform.training.application.internal.TrainingPdfService;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.dto.CancelRequest;
import za.co.handyflow.platform.training.dto.CertificateResponse;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Tag(name = "Training - Certificates", description = "Certificates issued for completed, certification-eligible enrollments")
public class TrainingCertificateController {

    private final TrainingCertificateService certificateService;
    private final TrainingPdfService pdfService;
    private final JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping("/certificates")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CertificateResponse>>> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(certificateService
                .list(TenantContext.getTenantIdAsObject(), employeeId, status, pageable)
                .map(this::toResponse)));
    }

    @GetMapping("/certificates/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<CertificateResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(certificateService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{enrollmentId}/certificate")
    // ADMIN-gated: issuing a certificate is this module's "financial commit
    // point" equivalent — a formal, potentially compliance-relevant document
    // asserting completion, same tier of consequence as CollAgency's
    // remittance / Warehousing's invoice generation, both ADMIN-only.
    @PreAuthorize("hasAuthority('TRAINING_ADMIN')")
    @Operation(summary = "Issue a certificate for a completed enrollment — ADMIN only")
    public ResponseEntity<ApiResponse<CertificateResponse>> issue(@PathVariable UUID enrollmentId) {
        featureGuard.requireModule("training");
        TrainingCertificate certificate = certificateService.issue(TenantContext.getTenantIdAsObject(), enrollmentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Certificate issued", toResponse(certificate)));
    }

    @PostMapping("/certificates/{id}/revoke")
    @PreAuthorize("hasAuthority('TRAINING_ADMIN')")
    @Operation(summary = "Revoke a previously issued certificate — ADMIN only")
    public ResponseEntity<ApiResponse<CertificateResponse>> revoke(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("training");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(certificateService.revoke(TenantContext.getTenantIdAsObject(), id, reason))));
    }

    @GetMapping("/certificates/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<byte[]> downloadCertificatePdf(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingCertificate certificate = certificateService.get(tenantId, id);
        byte[] pdf = pdfService.generateCertificate(certificate, fetchTenantName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + certificate.getCertificateNumber() + ".pdf\"")
                .body(pdf);
    }

    /** Same jdbc.queryForMap(tenants) fallback pattern HrController's own fetchTenantDetails() uses. */
    private String fetchTenantName(TenantId tenantId) {
        try {
            var row = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue());
            Object name = row.get("name");
            return name != null ? name.toString() : "HandyFlow Tenant";
        } catch (Exception e) {
            log.warn("Could not fetch tenant name for certificate PDF: {}", e.getMessage());
            return "HandyFlow Tenant";
        }
    }

    private CertificateResponse toResponse(TrainingCertificate c) {
        return new CertificateResponse(c.getId(), c.getEnrollmentId(), c.getEmployeeId(), c.getEmployeeNameSnapshot(),
                c.getCourseTitleSnapshot(), c.getCertificateNumber(), c.getIssueDate(), c.getExpiryDate(),
                c.getStatus(), c.getRevokedReason(), c.getCreatedAt());
    }
}
