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
import za.co.handyflow.platform.fuel.application.internal.FuelCashControlService;
import za.co.handyflow.platform.fuel.application.internal.FuelForecourtService;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.fuel.application.internal.FuelTankReconciliationService;
import za.co.handyflow.platform.fuel.dto.FuelRetailDtos;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.ReasonRequest;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stage 5: retail forecourt. Attendants (FUEL_FORECOURT) run shifts; managers (FUEL_MANAGE) set up pumps and reverse shifts. */
@RestController
@RequestMapping("/api/v1/fuel/forecourt")
@RequiredArgsConstructor
@Tag(name = "Fuel Forecourt", description = "Pumps, attendant shifts and takings")
public class FuelForecourtController {

    private static final String RUN = "hasAnyAuthority('FUEL_FORECOURT','FUEL_MANAGE','FUEL_FORECOURT_MANAGE')";
    private static final String SEE = "hasAnyAuthority('FUEL_READ','FUEL_FORECOURT','FUEL_MANAGE','FUEL_FORECOURT_MANAGE')";

    private static final String SUPERVISE = "hasAuthority('FUEL_SHIFT_SUPERVISE')";

    private final FuelForecourtService forecourt;
    private final FuelScopeService scopes;
    private final FuelCashControlService cash;
    private final FuelTankReconciliationService reconciliation;
    private final FeatureGuard featureGuard;

    /** D01: opening dip + recorded movements against the closing dip, and pump meters against the ledger. tolerancePct is optional. */
    @GetMapping("/tanks/{id}/reconciliation")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<FuelRetailDtos.TankReconciliation>> reconcileTank(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) BigDecimal tolerancePct) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(reconciliation.reconcile(TenantContext.getTenantIdAsObject(), id, from, to, tolerancePct)));
    }

    @GetMapping("/pumps")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<List<PumpResponse>>> pumps() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(forecourt.listPumps(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/pumps")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE')")
    public ResponseEntity<ApiResponse<PumpResponse>> createPump(@Valid @RequestBody PumpRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pump added", forecourt.createPump(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/pumps/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE')")
    public ResponseEntity<ApiResponse<PumpResponse>> updatePump(@PathVariable UUID id, @Valid @RequestBody PumpRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Pump updated", forecourt.updatePump(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/pumps/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deletePump(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        forecourt.deletePump(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Pump removed", null));
    }

    @GetMapping("/shifts")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> shifts(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(forecourt.listShifts(tenant, limit, scopes.allowedTankIds(tenant, FuelScopeService.currentUserOrNull()))));
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<ShiftResponse>> shift(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(forecourt.getShift(tenant, id, scopes.allowedTankIds(tenant, FuelScopeService.currentUserOrNull()))));
    }

    @PostMapping("/shifts")
    @PreAuthorize(RUN)
    public ResponseEntity<ApiResponse<ShiftResponse>> open(@Valid @RequestBody OpenShiftRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Shift opened",
                forecourt.openShift(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/shifts/{id}/close")
    @PreAuthorize(RUN)
    public ResponseEntity<ApiResponse<ShiftResponse>> close(@PathVariable UUID id, @Valid @RequestBody CloseShiftRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Shift closed", forecourt.closeShift(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/shifts/{id}/reverse")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE')")
    public ResponseEntity<ApiResponse<ShiftResponse>> reverse(@PathVariable UUID id, @Valid @RequestBody ReverseShiftRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Shift reversed", forecourt.reverseShift(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/shifts/{id}/adjustments")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<List<AdjustmentView>>> adjustments(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(cash.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/shifts/{id}/adjustments")
    @PreAuthorize(RUN)
    public ResponseEntity<ApiResponse<AdjustmentView>> recordAdjustment(@PathVariable UUID id, @Valid @RequestBody AdjustmentRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Recorded. A supervisor must approve it.",
                cash.record(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustments/{id}/approve")
    @PreAuthorize(SUPERVISE)
    public ResponseEntity<ApiResponse<AdjustmentView>> approveAdjustment(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionNote req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Approved", cash.approve(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustments/{id}/reject")
    @PreAuthorize(SUPERVISE)
    public ResponseEntity<ApiResponse<AdjustmentView>> rejectAdjustment(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Rejected", cash.reject(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustments/{id}/withdraw")
    @PreAuthorize(RUN)
    public ResponseEntity<ApiResponse<AdjustmentView>> withdrawAdjustment(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Withdrawn", cash.withdraw(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/shifts/{id}/review")
    @PreAuthorize(SUPERVISE)
    public ResponseEntity<ApiResponse<ShiftResponse>> reviewShift(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("fuel");
        cash.reviewShift(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Reviewed", forecourt.getShift(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/summary")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<ForecourtSummary>> summary(@RequestParam(defaultValue = "7") int days) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(forecourt.summary(tenant, days, scopes.allowedTankIds(tenant, FuelScopeService.currentUserOrNull()))));
    }
}
