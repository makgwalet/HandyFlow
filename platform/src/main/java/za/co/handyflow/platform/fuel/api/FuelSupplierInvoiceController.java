package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSupplierInvoiceService;
import za.co.handyflow.platform.fuel.dto.SupplierInvoiceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Supplier invoices matched against purchase orders and receipts. Approving and rejecting need FUEL_MANAGE. */
@RestController
@RequestMapping("/api/v1/fuel/supplier-invoices")
@RequiredArgsConstructor
@Tag(name = "Fuel Supplier Invoices", description = "Capture, three-way match, approve or reject supplier invoices")
public class FuelSupplierInvoiceController {

    private final FuelSupplierInvoiceService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> list(@RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), limit)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ReceiptLine>>> candidates(@RequestParam UUID supplierId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.candidates(TenantContext.getTenantIdAsObject(), supplierId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_SUPPLIER_INVOICE_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> capture(@Valid @RequestBody CaptureRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice captured",
                service.capture(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_SUPPLIER_INVOICE_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Invoice approved", service.approve(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    /** B08: hands an already-approved invoice to Accounts Payable (approvals do this themselves when AP is on). */
    @PostMapping("/{id}/send-to-ap")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_SUPPLIER_INVOICE_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> sendToAp(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Sent to Accounts Payable", service.sendToAp(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_SUPPLIER_INVOICE_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Invoice rejected", service.reject(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }
}
