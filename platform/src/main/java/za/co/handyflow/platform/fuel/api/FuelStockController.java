package za.co.handyflow.platform.fuel.api;

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
import za.co.handyflow.platform.fuel.application.internal.FuelStockAdjustmentApprovalService;
import za.co.handyflow.platform.fuel.application.internal.FuelStockService;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 1A: depots, stock ledger, adjustments, transfers, reservations, availability. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Stock", description = "Depots, stock ledger, transfers, reservations and availability")
public class FuelStockController {

    private final FuelStockService stock;
    private final FuelStockAdjustmentApprovalService adjustments;
    private final FeatureGuard featureGuard;
    private final za.co.handyflow.platform.fuel.application.internal.FuelScopeService scopeService;

    // ── Depots ───────────────────────────────────────────────────────────────

    @GetMapping("/depots")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<DepotResponse>>> depots() {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var all = stock.getDepots(tenant);
        String uid = TenantContext.getUserId();
        java.util.UUID user = null;
        if (uid != null && !TenantContext.isImpersonation()) { try { user = java.util.UUID.fromString(uid); } catch (IllegalArgumentException ignored) { } }
        var allowed = scopeService.allowedDepotIds(tenant, user);
        return ResponseEntity.ok(ApiResponse.success(allowed == null ? all : all.stream().filter(d -> allowed.contains(d.id())).toList()));
    }

    @PostMapping("/depots")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<DepotResponse>> createDepot(@Valid @RequestBody DepotRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Depot created",
                stock.createDepot(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/depots/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<DepotResponse>> updateDepot(@PathVariable UUID id, @Valid @RequestBody DepotRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Depot updated",
                stock.updateDepot(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/depots/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateDepot(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        stock.deactivateDepot(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/tanks/{id}/placement")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    @Operation(summary = "Assign a tank to a depot and set its safety stock")
    public ResponseEntity<ApiResponse<TankResponse>> placeTank(@PathVariable UUID id, @Valid @RequestBody TankPlacementRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tank updated",
                stock.placeTank(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Adjustments, transfers, ledger ───────────────────────────────────────

    @PostMapping("/tanks/{id}/adjust")
    @PreAuthorize("hasAuthority('FUEL_STOCK_ADJUST')")
    @Operation(summary = "Signed stock correction with a mandatory reason. Large ones wait for a second person.")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.AdjustOutcome>> adjust(@PathVariable UUID id, @Valid @RequestBody AdjustStockRequest req) {
        featureGuard.requireModule("fuel");
        FuelWholesaleDtos.AdjustOutcome out = adjustments.adjust(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(out.pendingApproval() ? "Sent for approval" : "Stock adjusted", out));
    }

    @GetMapping("/adjustment-requests")
    @PreAuthorize("hasAnyAuthority('FUEL_STOCK_ADJUST','FUEL_STOCK_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<List<FuelWholesaleDtos.AdjustmentRequestView>>> adjustmentRequests() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(adjustments.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/adjustment-requests/{id}/approve")
    @PreAuthorize("hasAuthority('FUEL_STOCK_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.AdjustmentRequestView>> approveAdjustment(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Adjustment approved and posted",
                adjustments.approve(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustment-requests/{id}/reject")
    @PreAuthorize("hasAuthority('FUEL_STOCK_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.AdjustmentRequestView>> rejectAdjustment(@PathVariable UUID id, @Valid @RequestBody FuelWholesaleDtos.ReasonRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Adjustment rejected",
                adjustments.reject(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustment-requests/{id}/withdraw")
    @PreAuthorize("hasAuthority('FUEL_STOCK_ADJUST')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.AdjustmentRequestView>> withdrawAdjustment(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Adjustment withdrawn",
                adjustments.withdraw(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@Valid @RequestBody TransferRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transfer recorded",
                stock.transfer(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<TransferResponse>>> transfers(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(stock.getTransfers(tenant, scopeService.allowedTankIds(tenant, FuelScopeService.currentUserOrNull()), pageable)));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Stock ledger, newest first; optionally for one tank")
    public ResponseEntity<ApiResponse<Page<MovementResponse>>> ledger(@RequestParam(required = false) UUID tankId,
                                                                      @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var user = FuelScopeService.currentUserOrNull();
        // Asking for one tank is checked like any path that names a tank.
        if (tankId != null) scopeService.requireTarget(tenant, user, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, tankId));
        return ResponseEntity.ok(ApiResponse.success(stock.getLedger(tenant, tankId, scopeService.allowedTankIds(tenant, user), pageable)));
    }

    // ── Reservations and availability ────────────────────────────────────────

    @PostMapping("/reservations")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(@Valid @RequestBody ReservationRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Stock reserved",
                stock.reserve(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/reservations/{id}/release")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> release(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Reservation released",
                stock.releaseReservation(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> reservations(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(stock.getReservations(tenant, scopeService.allowedTankIds(tenant, FuelScopeService.currentUserOrNull()), pageable)));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Physical, reserved, safety and available-to-promise litres per tank and per depot")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability() {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(stock.getAvailability(tenant, scopeService.allowedDepotIds(tenant, FuelScopeService.currentUserOrNull()))));
    }
}
