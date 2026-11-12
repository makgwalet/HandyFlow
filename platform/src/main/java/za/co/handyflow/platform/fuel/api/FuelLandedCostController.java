package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelLandedCostService;
import za.co.handyflow.platform.fuel.dto.LandedCostDtos.Report;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

/** Phase 6c: landed cost per litre and margin by route, tanker and depot. Read-only; it is cost and margin, so it needs the margin permission. */
@RestController
@RequestMapping("/api/v1/fuel/landed-cost")
@RequiredArgsConstructor
@Tag(name = "Fuel Landed Cost", description = "Landed cost per litre and margin")
public class FuelLandedCostController {

    private final FuelLandedCostService landed;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_MARGIN_READ')")
    public ResponseEntity<ApiResponse<Report>> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(landed.report(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
