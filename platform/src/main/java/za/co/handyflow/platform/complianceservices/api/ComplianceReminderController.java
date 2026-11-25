package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceReminderRunner;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceReminderService;
import za.co.handyflow.platform.complianceservices.dto.ReminderDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/compliance-services/reminders")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Reminders", description = "The daily digest of the work queue")
public class ComplianceReminderController {

    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceReminderService service;
    private final ComplianceReminderRunner runner;
    private final FeatureGuard featureGuard;

    @GetMapping("/settings")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<SettingsView>> update(@Valid @RequestBody SettingsRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), body, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/preview")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<PreviewView>>> preview() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), LocalDate.now(SAST))));
    }

    /** Sends today's digest now to anyone who has not had one today. */
    @PostMapping("/send-now")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RunResult>> sendNow() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(runner.run(TenantContext.getTenantIdAsObject(), LocalDate.now(SAST))));
    }

    @GetMapping("/log")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<LogView>>> log() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.log(TenantContext.getTenantIdAsObject())));
    }
}
