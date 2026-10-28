package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.application.internal.TrainingPdfService;
import za.co.handyflow.platform.training.application.internal.TrainingSelfService;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.dto.MyTrainingResponse;

import java.security.Principal;
import java.util.UUID;

/** An employee's own training: bookings and certificates. Needs only a signed-in login linked to an employee record. */
@RestController
@RequestMapping("/api/v1/training/mine")
@RequiredArgsConstructor
@Tag(name = "Training - My training", description = "An employee's own bookings and certificates")
public class TrainingSelfController {

    private final TrainingSelfService selfService;
    private final TrainingPdfService pdfService;
    private final JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyTrainingResponse>> mine(Principal principal) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(selfService.mine(TenantContext.getTenantIdAsObject(), user(principal))));
    }

    @GetMapping("/certificates/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable UUID id, Principal principal) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingCertificate certificate = selfService.ownCertificate(tenantId, user(principal), id);
        byte[] pdf = pdfService.generateCertificate(certificate, tenantName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + certificate.getCertificateNumber() + ".pdf\"")
                .body(pdf);
    }

    private static UUID user(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (RuntimeException e) {
            throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified.");
        }
    }

    private String tenantName(TenantId tenantId) {
        try {
            Object name = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue()).get("name");
            return name != null ? name.toString() : "HandyFlow Tenant";
        } catch (RuntimeException e) {
            return "HandyFlow Tenant";
        }
    }
}
