package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.Settings;
import za.co.handyflow.platform.shared.ApiResponse;

@RestController
@RequestMapping("/api/v1/internal-audit/settings")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Settings", description = "The rules internal audit runs by (read only)")
public class AuditSettingsController {
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Risk score bands, reminder timing and engagement roles")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(AuditSettings.current()));
    }
}
