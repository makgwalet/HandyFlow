package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.InspectionTemplateService;
import za.co.handyflow.platform.fleet.dto.InspectionTemplateResponse;
import za.co.handyflow.platform.fleet.dto.SaveInspectionTemplateRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Inspection checklists. FLEET_READ to view, FLEET_MANAGE to create/edit, FLEET_ADMIN to delete. */
@RestController
@RequestMapping("/api/v1/fleet/inspection-templates")
@RequiredArgsConstructor
@Tag(name = "Fleet - Inspection Templates", description = "Reusable inspection checklists")
public class InspectionTemplateController {

    private final InspectionTemplateService templateService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<List<InspectionTemplateResponse>>> list() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(templateService.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(templateService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> create(
            @Valid @RequestBody SaveInspectionTemplateRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template created",
                templateService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/starter")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Create the ready-made \"Daily vehicle inspection\" template (tyres, lights, fluids, body, safety)")
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> createStarter() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Starter template created",
                templateService.createStarter(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Replace a template's settings and checklist. Past inspections are unaffected.")
    public ResponseEntity<ApiResponse<InspectionTemplateResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SaveInspectionTemplateRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Template updated",
                templateService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        templateService.delete(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Template deleted", null));
    }
}