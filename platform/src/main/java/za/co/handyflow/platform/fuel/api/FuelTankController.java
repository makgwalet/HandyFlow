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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 5.2 — every endpoint in this controller was gated on
 * generic USER_READ/USER_CREATE/USER_UPDATE instead of this module's
 * own FUEL_READ/FUEL_MANAGE, unlike every other reviewed module (AP,
 * CRM, HR, Creative). FUEL_READ/FUEL_MANAGE/FUEL_ADMIN already existed
 * in the permission catalogue — auto-generated for every module by
 * AdminLookupService.createModule() — they were simply never
 * referenced here. Given this module's core value proposition is
 * theft/leak detection via dip-reading reconciliation, anyone holding
 * the broadly-granted default USER_CREATE/USER_READ (not a fuel-specific
 * role) could previously record dip readings or dispatch fuel,
 * undermining the segregation-of-duties story the negative-variance
 * alert exists to support.
 * <p>
 * Two tiers only (FUEL_READ / FUEL_MANAGE), matching the backlog's own
 * fix_required text exactly — no FUEL_ADMIN tier introduced here. This
 * is a deliberate difference from 8.1 (Accounting), where the backlog
 * explicitly called out three specific hard-to-undo actions (post,
 * reverse, close VAT period) warranting a stricter gate; nothing in
 * this finding asks for that here, so it wasn't invented.
 * <p>
 * No new permission migration needed — FUEL_READ/FUEL_MANAGE already
 * exist and are already auto-granted to every tenant's ADMIN role by
 * the same createModule() mechanism confirmed for every other module's
 * triplet this session (POPIA_EXPORT, ACCOUNTING_MANAGE/ADMIN). This is
 * a pure @PreAuthorize correction.
 */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel & Logistics", description = "Fuel inventory, dispatch, deliveries and reconciliation")
public class FuelTankController {

    private final FuelService              fuelService;
    private final FeatureGuard             featureGuard;
    private final DeliveryReceiptPdfService receiptPdfService;

    // ── Tanks ─────────────────────────────────────────────────────────────────

    @GetMapping("/tanks")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<TankResponse>>> getTanks() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getTanks(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/tanks/utilization-forecast")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Days-until-empty forecast for every active tank, based on recent dispatch usage")
    public ResponseEntity<ApiResponse<List<TankUtilizationForecastResponse>>> getUtilizationForecasts() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getUtilizationForecasts(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/tanks/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<TankResponse>> getTank(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getTank(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/tanks")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<TankResponse>> createTank(
            @Valid @RequestBody CreateTankRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tank created",
                        fuelService.createTank(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/tanks/{id}/reorder-suggestion")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Pre-fill data for a 'Receive stock' reorder — suggested quantity and last supplier")
    public ResponseEntity<ApiResponse<ReorderSuggestionResponse>> getReorderSuggestion(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getReorderSuggestion(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getSuppliers() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getSuppliers(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier added",
                        fuelService.createSupplier(TenantContext.getTenantIdAsObject(), request)));
    }

    // FIX: PUT instead of PATCH — avoids CORS preflight failures.
    // Was completely missing — SuppliersTab calls PUT /suppliers/{id}.
    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    @Operation(summary = "Update an existing fuel supplier's contact details")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSupplierRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Supplier updated",
                fuelService.updateSupplier(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/suppliers/{id}/statement")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Download a supplier statement/receiving report PDF — defaults to the current calendar month")
    public ResponseEntity<byte[]> downloadSupplierStatement(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        featureGuard.requireModule("fuel");
        byte[] pdf = fuelService.generateSupplierStatement(TenantContext.getTenantIdAsObject(), id, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"supplier-statement-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // ── Receipts ──────────────────────────────────────────────────────────────

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<ReceiptResponse>>> getReceipts(
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getReceipts(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/tanks/{id}/receive")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<ReceiptResponse>> receiveFuel(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveFuelRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel received",
                        fuelService.receiveFuel(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Dispatches ────────────────────────────────────────────────────────────

    @GetMapping("/dispatches")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<DispatchResponse>>> getDispatches(
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDispatches(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/tanks/{id}/dispatch")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DispatchResponse>> dispatchFuel(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchFuelRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel dispatched",
                        fuelService.dispatchFuel(TenantContext.getTenantIdAsObject(), id, request,
                                TenantContext.getCurrentUserId())));
    }

    // ── Dip Readings ──────────────────────────────────────────────────────────

    @GetMapping("/tanks/{id}/dip-readings")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<DipReadingResponse>>> getDipReadings(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDipReadings(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/tanks/{id}/dip-readings")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DipReadingResponse>> recordDipReading(
            @PathVariable UUID id,
            @Valid @RequestBody DipReadingRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dip reading recorded",
                        fuelService.recordDipReading(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/tanks/{id}/reconciliation-report")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Download a dip-reading reconciliation report PDF for a tank — defaults to the last 90 days")
    public ResponseEntity<byte[]> downloadReconciliationReport(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        featureGuard.requireModule("fuel");
        byte[] pdf = fuelService.generateReconciliationReport(TenantContext.getTenantIdAsObject(), id, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reconciliation-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @GetMapping("/dispatches/usage-report")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Download a fuel usage report PDF grouped by vehicle/recipient — defaults to the current calendar month")
    public ResponseEntity<byte[]> downloadUsageReport(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        featureGuard.requireModule("fuel");
        byte[] pdf = fuelService.generateUsageReport(TenantContext.getTenantIdAsObject(), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fuel-usage-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // ── Deliveries ────────────────────────────────────────────────────────────

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<DeliveryResponse>>> getDeliveries(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.getDeliveries(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> scheduleDelivery(
            @Valid @RequestBody CreateDeliveryRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery scheduled",
                        fuelService.scheduleDelivery(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/deliveries/{id}/complete")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> completeDelivery(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteDeliveryRequest request) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Delivery completed",
                fuelService.completeDelivery(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/deliveries/{id}/receipt")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        byte[] pdf = receiptPdfService.generateReceipt(id, TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"FDR-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}