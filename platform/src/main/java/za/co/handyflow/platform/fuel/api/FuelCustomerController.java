package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelCustomerService;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 1B: fuel customer accounts and their delivery sites. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Customers", description = "Customer accounts, credit terms and delivery sites")
public class FuelCustomerController {

    private final FuelCustomerService customers;
    private final FeatureGuard featureGuard;

    @GetMapping("/customer-accounts")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<CustomerAccountResponse>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(customers.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/customer-accounts/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<CustomerAccountResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(customers.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/customer-accounts")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<CustomerAccountResponse>> create(@Valid @RequestBody CustomerAccountRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Customer account created",
                customers.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/customer-accounts/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<CustomerAccountResponse>> update(@PathVariable UUID id, @Valid @RequestBody CustomerAccountRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Customer account updated",
                customers.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/customer-accounts/{id}/credit-hold")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<CustomerAccountResponse>> creditHold(@PathVariable UUID id, @Valid @RequestBody CreditHoldRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(req.hold() ? "Account placed on credit hold" : "Credit hold released",
                customers.setCreditHold(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/customer-accounts/{id}/sites")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<CustomerSiteResponse>> addSite(@PathVariable UUID id, @Valid @RequestBody CustomerSiteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Site added",
                customers.addSite(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/customer-sites/{siteId}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<CustomerSiteResponse>> updateSite(@PathVariable UUID siteId, @Valid @RequestBody CustomerSiteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Site updated",
                customers.updateSite(TenantContext.getTenantIdAsObject(), siteId, req)));
    }

    @DeleteMapping("/customer-sites/{siteId}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeSite(@PathVariable UUID siteId) {
        featureGuard.requireModule("fuel");
        customers.removeSite(TenantContext.getTenantIdAsObject(), siteId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
