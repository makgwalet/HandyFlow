package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.crm.application.internal.CustomerService;
import za.co.handyflow.platform.crm.dto.CreateCustomerRequest;
import za.co.handyflow.platform.crm.dto.CustomerResponse;
import za.co.handyflow.platform.crm.dto.UpdateCustomerRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/customers")
@RequiredArgsConstructor
@Tag(name = "CRM", description = "Customer and contact management")
public class CustomerController {

    private final CustomerService customerService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List all customers with pagination")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getCustomers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        featureGuard.requireModule("crm");
        var tenantId = TenantContext.getTenantIdAsObject();
        var customers = customerService.getCustomers(tenantId, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable UUID id
    ) {
        featureGuard.requireModule("crm");
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.getCustomer(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        featureGuard.requireModule("crm");
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.createCustomer(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created", customer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Update an existing customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        featureGuard.requireModule("crm");
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.updateCustomer(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated", customer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @Operation(summary = "Soft delete a customer")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
        featureGuard.requireModule("crm");
        var tenantId = TenantContext.getTenantIdAsObject();
        customerService.softDeleteCustomer(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted", null));
    }
}