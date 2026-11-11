package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelForecourtService;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 5: retail forecourt. Attendants (FUEL_FORECOURT) run shifts; managers (FUEL_MANAGE) set up pumps and reverse shifts. */
@RestController
@RequestMapping("/api/v1/fuel/forecourt")
@RequiredArgsConstructor
@Tag(name = "Fuel Forecourt", description = "Pumps, attendant shifts and takings")
public class FuelForecourtController {

    private static final String RUN = "hasAnyAuthority('FUEL_FORECOURT','FUEL_MANAGE')";
    private static final String SEE = "hasAnyAuthority('FUEL_READ','FUEL_FORECOURT','FUEL_MANAGE')";

    private final FuelForecourtService forecourt;
    private final FeatureGuard featureGuard;

    @GetMapping("/pumps")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<List<PumpResponse>>> pumps() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(forecourt.listPumps(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/pumps")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PumpResponse>> createPump(@Valid @RequestBody PumpRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pump added", forecourt.createPump(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/pumps/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PumpResponse>> updatePump(@PathVariable UUID id, @Valid @RequestBody PumpRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Pump updated", forecourt.updatePump(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/pumps/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deletePump(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        forecourt.deletePump(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Pump removed", null));
    }

    @GetMapping("/shifts")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> shifts(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(forecourt.listShifts(TenantContext.getTenantIdAsObject(), limit)));
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<ShiftResponse>> shift(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(forecourt.getShift(TenantContext.getTenantIdAsObject(), id)));
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
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<ShiftResponse>> reverse(@PathVariable UUID id, @Valid @RequestBody ReverseShiftRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Shift reversed", forecourt.reverseShift(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/summary")
    @PreAuthorize(SEE)
    public ResponseEntity<ApiResponse<ForecourtSummary>> summary(@RequestParam(defaultValue = "7") int days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(forecourt.summary(TenantContext.getTenantIdAsObject(), days)));
    }
}
