package za.co.handyflow.platform.billing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.application.internal.ModuleService;
import za.co.handyflow.platform.billing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/modules")
@RequiredArgsConstructor
@Tag(name = "Modules", description = "Module catalogue and tenant module management")
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping
    @Operation(summary = "List all available modules — public, no auth required")
    public ResponseEntity<ApiResponse<List<ModuleCatalogueResponse>>> getCatalogue() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                moduleService.getCatalogue()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @Operation(summary = "List modules active for this tenant")
    public ResponseEntity<ApiResponse<List<TenantModuleResponse>>> getMyModules() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                moduleService.getTenantModules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/activate")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Activate a module — starts 60-day trial if not already active")
    public ResponseEntity<ApiResponse<TenantModuleResponse>> activateModule(
            @Valid @RequestBody ActivateModuleRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Module activated",
                moduleService.activateModule(
                        TenantContext.getTenantIdAsObject(), req.moduleKey(), 60)));
    }

    @PostMapping("/activate-batch")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Activate multiple modules at once — used during onboarding")
    public ResponseEntity<ApiResponse<List<TenantModuleResponse>>> activateBatch(
            @Valid @RequestBody OnboardingModuleSelection req) {
        var tenantId = TenantContext.getTenantIdAsObject();
        int trialDays = req.trialDays() > 0 ? req.trialDays() : 60;
        moduleService.activateModules(tenantId, req.moduleKeys(), trialDays);
        return ResponseEntity.ok(ApiResponse.success("Modules activated",
                moduleService.getTenantModules(tenantId)));
    }

    @DeleteMapping("/{moduleKey}")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Cancel a module — access continues until end of billing period")
    public ResponseEntity<ApiResponse<CancelPreviewResponse>> cancelModule(
            @PathVariable String moduleKey) {
        var tenantId = TenantContext.getTenantIdAsObject();
        // Get preview before cancelling so we can return accessUntil
        var preview = moduleService.getCancelPreview(tenantId, moduleKey);
        moduleService.cancelModule(tenantId, moduleKey);
        return ResponseEntity.ok(ApiResponse.success(
                "Module cancelled — access continues until " + preview.accessUntil(),
                preview));
    }

    @GetMapping("/access/{moduleKey}")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @Operation(summary = "Check if tenant has access to a specific module")
    public ResponseEntity<ApiResponse<Boolean>> checkAccess(
            @PathVariable String moduleKey) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                moduleService.hasAccess(
                        TenantContext.getTenantIdAsObject(), moduleKey)));
    }

    @GetMapping("/{moduleKey}/cancel-preview")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @Operation(summary = "Preview impact of cancelling a module — shows affected record count and grace period end date")
    public ResponseEntity<ApiResponse<CancelPreviewResponse>> cancelPreview(
            @PathVariable String moduleKey) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                moduleService.getCancelPreview(
                        TenantContext.getTenantIdAsObject(), moduleKey)));
    }
}