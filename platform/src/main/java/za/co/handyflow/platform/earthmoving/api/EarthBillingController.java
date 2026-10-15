package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthMonthlyBillingService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving")
@RequiredArgsConstructor
@Tag(name = "Earthmoving monthly billing")
public class EarthBillingController {

    private final EarthMonthlyBillingService billing;
    private final FeatureGuard featureGuard;

    @GetMapping("/billing/preview")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "What the monthly run would charge for a finished month, and what needs a person")
    public ResponseEntity<ApiResponse<BillingRunResponse>> preview(@RequestParam String month) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Billing preview", billing.preview(TenantContext.getTenantIdAsObject(), month)));
    }

    @PostMapping("/billing/run")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Run a finished month's billing: raise the charges and, if asked, draft one invoice per customer")
    public ResponseEntity<ApiResponse<BillingRunResponse>> run(@Valid @RequestBody BillingRunRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Billing run done",
                billing.run(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @GetMapping("/billing/runs")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Past billing runs, and whether the scheduled run is on")
    public ResponseEntity<ApiResponse<BillingHistoryResponse>> history() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Billing runs", billing.history(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/billing/scheduled")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Switch the scheduled monthly run on or off for the company")
    public ResponseEntity<ApiResponse<BillingHistoryResponse>> scheduled(@RequestBody AutoBillingSwitchRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Scheduled billing updated",
                billing.switchScheduled(TenantContext.getTenantIdAsObject(), request.enabled(), UserContext.getCurrentUserId())));
    }

    @GetMapping("/deployments/{id}/auto-billing")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "How, if at all, the monthly run bills this deployment")
    public ResponseEntity<ApiResponse<AutoBillingResponse>> basis(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Automatic billing", billing.deploymentBasis(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/deployments/{id}/auto-billing")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Put the deployment on the monthly run with a basis, or off with a null basis")
    public ResponseEntity<ApiResponse<AutoBillingResponse>> setBasis(@PathVariable UUID id, @RequestBody AutoBillingRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Automatic billing updated",
                billing.setDeploymentBasis(TenantContext.getTenantIdAsObject(), id, request.basis(), UserContext.getCurrentUserId())));
    }
}
