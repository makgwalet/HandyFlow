package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelTabInvoicingService;
import za.co.handyflow.platform.fuel.application.internal.FuelTabService;
import za.co.handyflow.platform.fuel.dto.TabDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Driver tabs at the truck stop. Under /truck-stop, so the server requires the Truck Stop division. */
@Slf4j
@RestController
@RequestMapping("/api/v1/fuel/truck-stop/tabs")
@RequiredArgsConstructor
@Tag(name = "Fuel Truck Stop Tabs", description = "A driver's bill of truck stop services, paid at the till or charged to an account")
public class FuelTabController {

    private static final String READ = "hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')";
    private static final String WRITE = "hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')";

    private final FuelTabService service;
    private final FuelTabInvoicingService invoicing;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<TabView>>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> open(@Valid @RequestBody OpenRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tab opened",
                service.open(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}/account")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> account(@PathVariable UUID id, @RequestBody AccountRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.setAccount(TenantContext.getTenantIdAsObject(), id, req.accountId())));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> addLine(@PathVariable UUID id, @Valid @RequestBody AddLineRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.addLine(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.removeLine(TenantContext.getTenantIdAsObject(), id, lineId)));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> settle(@PathVariable UUID id, @Valid @RequestBody SettleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tab settled",
                service.settle(TenantContext.getTenantIdAsObject(), id, req.paymentMethod(), TenantContext.getCurrentUserId())));
    }

    /** Charges the tab to its account, then tries to make the draft invoice. A failed invoice does not undo the charge; use the invoice route to retry. */
    @PostMapping("/{id}/charge")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> charge(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        TabView charged = service.chargeToAccount(tenant, id, TenantContext.getCurrentUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Tab charged to the account and invoice drafted", invoicing.invoice(tenant, id)));
        } catch (RuntimeException e) {
            log.warn("Tab {} charged but not invoiced: {}", id, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success("Tab charged to the account, but the invoice was not raised: " + e.getMessage(), charged));
        }
    }

    @PostMapping("/{id}/invoice")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> invoice(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Invoice drafted", invoicing.invoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TabView>> voidTab(@PathVariable UUID id, @Valid @RequestBody VoidRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tab voided",
                service.voidTab(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }
}
