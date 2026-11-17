package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelImportAutoAcceptService;
import za.co.handyflow.platform.fuel.dto.AutoAcceptDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Whether clean imports are accepted automatically. Needs the configuration permission. Not under /forecourt: it covers gauge readings too. */
@RestController
@RequestMapping("/api/v1/fuel/import-auto-accept")
@RequiredArgsConstructor
@Tag(name = "Fuel Import Auto-accept", description = "Accept clean imported shifts and gauge readings automatically (off by default)")
public class FuelImportAutoAcceptController {

    private final FuelImportAutoAcceptService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<Setting>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<Setting>> save(@Valid @RequestBody SaveRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", service.save(TenantContext.getTenantIdAsObject(),
                req.shiftsEnabled(), req.gaugesEnabled(), req.gaugeMaxDifferenceLitres(), TenantContext.getCurrentUserId())));
    }

    /** Every shift accepted automatically: variance, tolerance in force, who and when. */
    @GetMapping("/log")
    @PreAuthorize("hasAnyAuthority('FUEL_CONFIGURATION_MANAGE','FUEL_SHIFT_SUPERVISE')")
    public ResponseEntity<ApiResponse<java.util.List<za.co.handyflow.platform.fuel.dto.ShiftControlDtos.AutoAcceptLogView>>> log(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.log(TenantContext.getTenantIdAsObject(), limit)));
    }
}
