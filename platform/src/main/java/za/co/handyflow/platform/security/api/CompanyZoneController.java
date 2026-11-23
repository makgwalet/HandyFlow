// security/api/CompanyZoneController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CompanyZoneRules;
import za.co.handyflow.platform.security.application.internal.CompanyZoneService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Company time zone")
public class CompanyZoneController {

    public record ChangeRequest(@NotBlank @Size(max = 60) String timeZone, boolean confirm) {}

    private final CompanyZoneService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/company-time-zone")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The time zone this company's days, weeks and months are decided in")
    public ResponseEntity<ApiResponse<CompanyZoneService.View>> get() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.get(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/api/v1/security/company-time-zone/preview")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "What changing to a zone would affect (changes nothing)")
    public ResponseEntity<ApiResponse<CompanyZoneRules.Effect>> preview(@RequestParam String timeZone) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.preview(TenantContext.getTenantIdAsObject(), timeZone)));
    }

    @PutMapping("/api/v1/security/company-time-zone")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Change the company's time zone",
            description = "Needs confirm=true after reading the preview. Moments stay stored in UTC; only the day, week and month boundaries move. Audited.")
    public ResponseEntity<ApiResponse<CompanyZoneService.Changed>> change(@Valid @RequestBody ChangeRequest r) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK",
                service.change(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), r.timeZone(), r.confirm())));
    }
}
