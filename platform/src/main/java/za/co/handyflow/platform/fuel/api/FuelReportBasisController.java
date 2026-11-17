package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSettingsService;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.ReportBasis;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Which time dates a shift in shifts-by-date reports, and the timezone that decides the day. Shared foundation, so no division rule. */
@RestController
@RequestMapping("/api/v1/fuel/report-basis")
@RequiredArgsConstructor
@Tag(name = "Fuel Report Basis", description = "Provider or system times in shifts-by-date reports")
public class FuelReportBasisController {

    private final FuelSettingsService settings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<ReportBasis>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.reportBasis(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<ReportBasis>> save(@Valid @RequestBody ReportBasis req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateReportBasis(TenantContext.getTenantIdAsObject(), req)));
    }

}
