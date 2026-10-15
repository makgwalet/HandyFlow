package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthReportService;
import za.co.handyflow.platform.earthmoving.dto.ProfitabilityResponse;
import za.co.handyflow.platform.earthmoving.dto.UtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.MachineUtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.CustomerRevenueResponse;
import za.co.handyflow.platform.earthmoving.dto.DowntimeResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/earthmoving/reports")
@RequiredArgsConstructor
@Tag(name = "Earthmoving reports")
public class EarthReportController {

    private final EarthReportService reports;
    private final FeatureGuard featureGuard;

    @GetMapping("/profitability")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Hire revenue against maintenance cost per machine for a period")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> profitability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Profitability", reports.profitability(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/utilisation")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Hours worked per day on hour-metered machines over the last N days")
    public ResponseEntity<ApiResponse<UtilisationResponse>> utilisation(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Utilisation", reports.utilisation(TenantContext.getTenantIdAsObject(), days)));
    }

    @GetMapping("/machine-utilisation")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Hours worked per hour-metered machine for a period")
    public ResponseEntity<ApiResponse<MachineUtilisationResponse>> machineUtilisation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Machine utilisation", reports.machineUtilisation(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/customer-revenue")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Hire revenue per customer for a period")
    public ResponseEntity<ApiResponse<CustomerRevenueResponse>> customerRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Customer revenue", reports.customerRevenue(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/downtime")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Time in breakdown and in maintenance per machine for a period")
    public ResponseEntity<ApiResponse<DowntimeResponse>> downtime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Downtime", reports.downtime(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
