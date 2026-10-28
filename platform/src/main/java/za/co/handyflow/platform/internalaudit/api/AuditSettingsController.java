package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.Settings;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.Values;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettingsService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/internal-audit/settings")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Settings", description = "The rules internal audit runs by: risk score bands and reminder timing")
public class AuditSettingsController {
    private final AuditSettingsService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Risk score bands, reminder timing and engagement roles")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject().getValue())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Change the company's risk score bands and reminder timing. New assessments use the new bands; existing ones keep their level.")
    public ResponseEntity<ApiResponse<Settings>> update(@RequestBody Values values) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), values, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Go back to the standard rules")
    public ResponseEntity<ApiResponse<Settings>> reset() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.reset(TenantContext.getTenantIdAsObject())));
    }
}
