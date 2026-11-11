package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelLegalEntityService;
import za.co.handyflow.platform.fuel.application.internal.FuelOperatingProfileService;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Fuel setup: operating profile and divisions, legal entities, depot ownership. Exempt from the division rules by design. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Operating Profile", description = "Divisions, setup wizard and legal entities")
public class FuelOperatingProfileController {

    private static final String CONFIG_MANAGE = "hasAuthority('FUEL_CONFIGURATION_MANAGE')";
    private static final String CONFIG_READ = "hasAnyAuthority('FUEL_CONFIGURATION_READ','FUEL_CONFIGURATION_MANAGE')";

    private final FuelOperatingProfileService profile;
    private final FuelLegalEntityService entities;
    private final FeatureGuard featureGuard;

    @GetMapping("/operating-profile")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<ProfileView>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(profile.view(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/operating-profile/wizard")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<ProfileView>> saveWizard(@Valid @RequestBody WizardRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", profile.saveWizard(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/operating-profile/activate")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<ProfileView>> activate(@Valid @RequestBody ActivateRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Activated", profile.activate(TenantContext.getTenantIdAsObject(), req.divisions(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/operating-profile/divisions/{division}/activate")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<ProfileView>> activateDivision(@PathVariable FuelDivision division) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Activated", profile.activateDivision(TenantContext.getTenantIdAsObject(), division, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/operating-profile/divisions/{division}/deactivate")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<ProfileView>> deactivateDivision(@PathVariable FuelDivision division, @Valid @RequestBody DeactivateRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Deactivated", profile.deactivateDivision(TenantContext.getTenantIdAsObject(), division, req.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/legal-entities")
    @PreAuthorize(CONFIG_READ)
    public ResponseEntity<ApiResponse<List<LegalEntityResponse>>> listEntities() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(entities.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/legal-entities")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<LegalEntityResponse>> createEntity(@Valid @RequestBody LegalEntityRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Created", entities.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/legal-entities/{id}")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<LegalEntityResponse>> updateEntity(@PathVariable UUID id, @Valid @RequestBody LegalEntityRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Updated", entities.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/legal-entities/{id}")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<Void>> removeEntity(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        entities.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @PutMapping("/depots/{id}/legal-entity")
    @PreAuthorize(CONFIG_MANAGE)
    public ResponseEntity<ApiResponse<Void>> assignDepot(@PathVariable UUID id, @RequestBody DepotEntityRequest req) {
        featureGuard.requireModule("fuel");
        entities.assignDepot(TenantContext.getTenantIdAsObject(), id, req.legalEntityId());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }
}
