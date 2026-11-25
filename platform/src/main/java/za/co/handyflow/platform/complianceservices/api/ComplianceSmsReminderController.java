package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceSmsReminderRunner;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceSmsReminderService;
import za.co.handyflow.platform.complianceservices.dto.SmsReminderDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/sms-reminders")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - SMS reminders", description = "Deadline reminders by SMS, only to numbers with recorded consent")
public class ComplianceSmsReminderController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceSmsReminderService service;
    private final ComplianceSmsReminderRunner runner;
    private final FeatureGuard featureGuard;

    @GetMapping("/settings")
    @PreAuthorize(READ)
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

    @GetMapping("/clients/{clientId}/consents")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ConsentView>>> consents(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.consentsFor(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/clients/{clientId}/consents")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ConsentView>> record(@PathVariable UUID clientId, @Valid @RequestBody ConsentRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.recordConsent(TenantContext.getTenantIdAsObject(), clientId, body, TenantContext.getCurrentUserId())));
    }

    /** Takes effect at once: the next text checks consent again before it goes. */
    @PostMapping("/consents/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ConsentView>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.withdraw(TenantContext.getTenantIdAsObject(), id, body, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/log")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LogView>>> log(@RequestParam(required = false) UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.log(TenantContext.getTenantIdAsObject(), clientId)));
    }

    /** Sends today's reminders now to anyone who has not had them. */
    @PostMapping("/send-now")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RunResult>> sendNow() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(runner.run(TenantContext.getTenantIdAsObject(), LocalDate.now(SAST))));
    }
}
