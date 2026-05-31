// fuel/api/FuelTankController.java

package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.DeliveryReceiptPdfService;
import za.co.handyflow.platform.fuel.application.internal.FuelService;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel & Logistics", description = "Fuel inventory, dispatch, deliveries and reconciliation")
public class FuelTankController {

    private final FuelService  fuelService;
    private final FeatureGuard featureGuard;
    private final DeliveryReceiptPdfService receiptPdfService;

    // ── Tanks ─────────────────────────────────────────────────────────────────

    @GetMapping("/tanks")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all tanks with current levels and fill percentage")
    public ResponseEntity<ApiResponse<List<TankResponse>>> getTanks() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getTanks(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/tanks/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<TankResponse>> getTank(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getTank(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/tanks")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new fuel tank")
    public ResponseEntity<ApiResponse<TankResponse>> createTank(
            @Valid @RequestBody CreateTankRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tank created",
                        fuelService.createTank(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getSuppliers() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getSuppliers(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a fuel supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier added",
                        fuelService.createSupplier(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Receipts ──────────────────────────────────────────────────────────────

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all fuel receipts across all tanks")
    public ResponseEntity<ApiResponse<Page<ReceiptResponse>>> getReceipts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getReceipts(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/tanks/{id}/receive")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Receive fuel into tank — validates capacity, updates stock level")
    public ResponseEntity<ApiResponse<ReceiptResponse>> receiveFuel(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveFuelRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel received",
                        fuelService.receiveFuel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Dispatches ────────────────────────────────────────────────────────────

    @GetMapping("/dispatches")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all fuel dispatches")
    public ResponseEntity<ApiResponse<Page<DispatchResponse>>> getDispatches(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDispatches(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/tanks/{id}/dispatch")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Dispatch fuel from tank — validates sufficient stock, deducts level")
    public ResponseEntity<ApiResponse<DispatchResponse>> dispatchFuel(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchFuelRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel dispatched",
                        fuelService.dispatchFuel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Dip Readings ──────────────────────────────────────────────────────────

    @GetMapping("/tanks/{id}/dip-readings")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get dip reading history for a tank")
    public ResponseEntity<ApiResponse<Page<DipReadingResponse>>> getDipReadings(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDipReadings(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/tanks/{id}/dip-readings")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Record a dip reading — calculates variance vs system level")
    public ResponseEntity<ApiResponse<DipReadingResponse>> recordDipReading(
            @PathVariable UUID id,
            @Valid @RequestBody DipReadingRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dip reading recorded",
                        fuelService.recordDipReading(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Deliveries ────────────────────────────────────────────────────────────

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List deliveries, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<DeliveryResponse>>> getDeliveries(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDeliveries(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Schedule a fuel delivery to a client site")
    public ResponseEntity<ApiResponse<DeliveryResponse>> scheduleDelivery(
            @Valid @RequestBody CreateDeliveryRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery scheduled",
                        fuelService.scheduleDelivery(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/deliveries/{id}/complete")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Complete a delivery — records actual litres delivered, deducts from tank")
    public ResponseEntity<ApiResponse<DeliveryResponse>> completeDelivery(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteDeliveryRequest request
    ) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Delivery completed",
                fuelService.completeDelivery(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/deliveries/{id}/receipt")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download fuel delivery receipt PDF — proof of delivery for mine sites")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        var tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = receiptPdfService.generateReceipt(id, tenantId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"FDR-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}