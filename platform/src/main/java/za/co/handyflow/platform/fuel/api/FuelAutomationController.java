package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelAutomationService;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Phase 6b: reorder rules the company sets, what they did, and the overdue-order alert switch. A rule can only draft a requisition. */
@RestController
@RequestMapping("/api/v1/fuel/automation")
@RequiredArgsConstructor
@Tag(name = "Fuel Automation", description = "Reorder rules and alerts")
public class FuelAutomationController {

    private final FuelAutomationService automation;
    private final FeatureGuard featureGuard;

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<RuleView>>> rules() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(automation.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RuleView>> create(@Valid @RequestBody RuleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rule added", automation.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RuleView>> update(@PathVariable UUID id, @Valid @RequestBody RuleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Rule updated", automation.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        automation.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Rule removed", null));
    }

    /** Runs the rules now instead of waiting for the daily run. It makes real draft requisitions, never submits or orders anything. */
    @PostMapping("/run")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RunResult>> run() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(automation.runRules(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/log")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<LogView>>> log() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(automation.log(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<AutomationSettings>> settings() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(automation.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<AutomationSettings>> updateSettings(@RequestBody AutomationSettings req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", automation.updateSettings(TenantContext.getTenantIdAsObject(), req.alertOverduePurchaseOrders())));
    }
}
