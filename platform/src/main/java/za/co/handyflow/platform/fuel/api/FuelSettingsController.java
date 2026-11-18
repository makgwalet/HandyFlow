package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSettingsService;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsRequest;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/fuel/settings")
@RequiredArgsConstructor
@Tag(name = "Fuel Settings", description = "Cost-to-serve rates and delivery tolerance")
public class FuelSettingsController {

    private final FuelSettingsService settings;
    private final FeatureGuard featureGuard;
    private final za.co.handyflow.platform.fuel.application.internal.FuelPodPolicyService pod;
    private final za.co.handyflow.platform.fuel.application.internal.FuelEnforcementReadinessService readiness;
    private final za.co.handyflow.platform.fuel.application.internal.FuelEnforcementService enforcement;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<SettingsResponse>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.get(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/controls")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.ControlsResponse>> controls() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.controls(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/controls")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.ControlsResponse>> updateControls(@RequestBody FuelWholesaleDtos.ControlsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateControls(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/arrival-check")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_DRIVER')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalPolicy>> arrivalPolicy() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.arrivalPolicy(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/arrival-check")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalPolicy>> updateArrivalPolicy(@Valid @RequestBody za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalPolicyRequest req) {
        featureGuard.requireModule("fuel");
        if (req.enforced() != settings.arrivalPolicy(TenantContext.getTenantIdAsObject()).enforced())
            throw new IllegalStateException("The arrival check is rolled out in stages. Change it with the enforcement controls: Monitor, then Pilot, then Enforced.");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateArrivalPolicy(TenantContext.getTenantIdAsObject(), req)));
    }

    /** Before switching a policy on: how many deliveries of the last N days (default 30) it would have refused. Read only. */
    @GetMapping("/enforcement-readiness")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.EnforcementDtos.Readiness>> enforcementReadiness(@RequestParam(required = false) Integer days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(readiness.readiness(TenantContext.getTenantIdAsObject(), days)));
    }

    /** The rollout state of each control. */
    @GetMapping("/enforcement")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<java.util.List<za.co.handyflow.platform.fuel.dto.EnforcementDtos.ControlState>>> enforcementStates() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(enforcement.list(TenantContext.getTenantIdAsObject())));
    }

    /** What stands in the way of moving a control to a state, as a plain list. Changes nothing. */
    @GetMapping("/enforcement/{control}/blockers")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.EnforcementDtos.Blockers>> enforcementBlockers(
            @PathVariable String control, @RequestParam String target, @RequestParam(required = false) java.util.List<java.util.UUID> depots, @RequestParam(required = false) String reason) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(enforcement.blockers(TenantContext.getTenantIdAsObject(), control, target, depots, reason)));
    }

    @PutMapping("/enforcement/{control}")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.EnforcementDtos.ControlState>> changeEnforcement(
            @PathVariable String control, @Valid @RequestBody za.co.handyflow.platform.fuel.dto.EnforcementDtos.ChangeRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", enforcement.change(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), control, req)));
    }

    /** What the control would have refused while it was only watching. */
    @GetMapping("/enforcement/{control}/observations")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<java.util.List<za.co.handyflow.platform.fuel.dto.EnforcementDtos.Observation>>> enforcementObservations(
            @PathVariable String control, @RequestParam(required = false) Integer days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(enforcement.recentObservations(TenantContext.getTenantIdAsObject(), control, days)));
    }

    @GetMapping("/pod-policy")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_DRIVER')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicy>> podPolicy() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(pod.policy(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/pod-policy")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicy>> updatePodPolicy(@RequestBody za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicyRequest req) {
        featureGuard.requireModule("fuel");
        if (req.enforced() != pod.policy(TenantContext.getTenantIdAsObject()).enforced())
            throw new IllegalStateException("The POD policy is rolled out in stages. Change it with the enforcement controls: Monitor, then Pilot, then Enforced.");
        return ResponseEntity.ok(ApiResponse.success("Saved", pod.updatePolicy(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/emergency-contact")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<FuelSettingsDtos.EmergencyContact>> updateEmergencyContact(@Valid @RequestBody FuelSettingsDtos.EmergencyContact req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateEmergencyContact(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<SettingsResponse>> update(@Valid @RequestBody SettingsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.update(TenantContext.getTenantIdAsObject(), req)));
    }
}
