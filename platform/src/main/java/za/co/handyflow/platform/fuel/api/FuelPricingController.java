package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPricingService;
import za.co.handyflow.platform.fuel.dto.PriceRequest;
import za.co.handyflow.platform.fuel.dto.PriceResponse;
import za.co.handyflow.platform.fuel.dto.ResolvedPriceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Stage 4A: price lists, contract prices and the price that applies. */
@RestController
@RequestMapping("/api/v1/fuel/prices")
@RequiredArgsConstructor
@Tag(name = "Fuel Pricing", description = "Standard and contract prices with volume breaks")
public class FuelPricingController {

    private final FuelPricingService pricing;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<PriceResponse>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(pricing.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<PriceResponse>> create(@Valid @RequestBody PriceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Price added",
                pricing.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> end(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastDay) {
        featureGuard.requireModule("fuel");
        pricing.end(TenantContext.getTenantIdAsObject(), id, lastDay);
        return ResponseEntity.ok(ApiResponse.success("Price ended", null));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<ResolvedPriceResponse>> resolve(@RequestParam(required = false) UUID accountId,
            @RequestParam String fuelType, @RequestParam(required = false) BigDecimal litres,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(pricing.resolve(TenantContext.getTenantIdAsObject(), accountId, fuelType, litres,
                date != null ? date : LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")))
                .orElseThrow(() -> new ResourceNotFoundException("Price", fuelType))));
    }
}
