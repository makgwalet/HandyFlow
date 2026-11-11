package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelInvoicingService;
import za.co.handyflow.platform.fuel.application.internal.FuelOrderService;
import za.co.handyflow.platform.fuel.application.internal.FuelService;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 2: sales orders, loading records and the delivered-not-invoiced queue. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Orders", description = "Quotations, orders, loading and invoicing hand-off")
public class FuelOrderController {

    private final FuelOrderService orders;
    private final FuelInvoicingService invoicing;
    private final FuelService fuel;
    private final FeatureGuard featureGuard;

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> list(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(orders.list(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(orders.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/orders/{id}/credit-check")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<CreditCheckResponse>> creditCheck(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(orders.creditCheck(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Quotation created",
                orders.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> revise(@PathVariable UUID id, @Valid @RequestBody OrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Quotation updated", orders.revise(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/orders/{id}/confirm")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmOrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Order confirmed and fuel allocated",
                orders.confirm(TenantContext.getTenantIdAsObject(), id, req, canOverrideCredit(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody OrderCancelRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", orders.cancel(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/orders/{id}/deliveries")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> scheduleLoad(@PathVariable UUID id, @Valid @RequestBody OrderDeliveryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Load scheduled",
                orders.scheduleLoad(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/deliveries/{id}/loading")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> loading(@PathVariable UUID id, @Valid @RequestBody LoadingRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Loading recorded", fuel.recordLoading(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/invoicing/queue")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<InvoiceQueueLine>>> queue() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(invoicing.queue(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/invoicing/invoice")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceCreatedResponse>> invoice(@Valid @RequestBody CreateInvoiceFromDeliveriesRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft invoice created",
                invoicing.invoice(TenantContext.getTenantIdAsObject(), req)));
    }

    private static boolean canOverrideCredit() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_CREDIT_OVERRIDE".equals(g.getAuthority()));
    }
}
