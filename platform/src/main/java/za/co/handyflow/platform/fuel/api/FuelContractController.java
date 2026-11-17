package za.co.handyflow.platform.fuel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelContractService;
import za.co.handyflow.platform.fuel.dto.ContractDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Phase 7c (B06b): supply contracts that group and report. They hold no prices; the price list stays the only price master. */
@RestController
@RequestMapping("/api/v1/fuel/contracts")
@RequiredArgsConstructor
public class FuelContractController {

    private final FuelContractService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ContractView>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), today())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<ContractView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<ContractView>> create(@Valid @RequestBody CreateRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Contract saved", service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<ContractView>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Contract saved", service.update(TenantContext.getTenantIdAsObject(), id, req, today())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<ContractView>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Contract cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req, today())));
    }

    @PutMapping("/{id}/prices/{priceId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<ContractView>> linkPrice(@PathVariable UUID id, @PathVariable UUID priceId,
                                                                @jakarta.validation.Valid @RequestBody(required = false) za.co.handyflow.platform.fuel.dto.ContractDtos.LinkRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Price linked", service.linkPrice(TenantContext.getTenantIdAsObject(), id, priceId,
                req == null ? null : req.fromLitres(), TenantContext.getCurrentUserId(), today())));
    }

    @DeleteMapping("/{id}/prices/{priceId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_CUSTOMERS_MANAGE','FUEL_PRICING_MANAGE')")
    public ResponseEntity<ApiResponse<ContractView>> unlinkPrice(@PathVariable UUID id, @PathVariable UUID priceId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Price unlinked", service.unlinkPrice(TenantContext.getTenantIdAsObject(), id, priceId, today())));
    }
}
