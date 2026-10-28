package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import za.co.handyflow.platform.training.application.internal.TrainingSelfBookingService;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.BookRequest;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.OpenSession;
import java.util.List;
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
    private final TrainingSelfBookingService bookingService;
    private final JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyTrainingResponse>> mine(Principal principal) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(selfService.mine(TenantContext.getTenantIdAsObject(), user(principal))));
    }

    /** Sessions the caller may book themselves onto. */
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OpenSession>>> openSessions(Principal principal) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(bookingService.open(TenantContext.getTenantIdAsObject(), user(principal))));
    }

    @PostMapping("/bookings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> book(@Valid @RequestBody BookRequest req, Principal principal) {
        featureGuard.requireModule("training");
        bookingService.book(TenantContext.getTenantIdAsObject(), user(principal), req.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("You are booked", null));
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, Principal principal) {
        featureGuard.requireModule("training");
        bookingService.cancel(TenantContext.getTenantIdAsObject(), user(principal), id);
        return ResponseEntity.ok(ApiResponse.success("Your booking was cancelled", null));
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
