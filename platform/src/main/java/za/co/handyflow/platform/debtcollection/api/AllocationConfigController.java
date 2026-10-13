package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationConfigService;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationService;
import za.co.handyflow.platform.debtcollection.application.internal.CollectorProfileService;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationRule;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Allocation configuration: the on/off switch and default strategy, the collector register (with live load) and routing rules. Changes are supervisor-only. */
@RestController
@RequestMapping("/api/v1/debtcollection/allocation")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Allocation Setup", description = "Collectors, routing rules and the allocation switch")
public class AllocationConfigController {

    private final AllocationConfigService configService;
    private final CollectorProfileService profileService;
    private final AllocationService allocationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<AllocationSettingsResponse>> settings() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(AllocationSettingsResponse.of(configService.settings(tenant()))));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Turn automatic allocation on/off and set the default strategy (opt-in — review a preview first)")
    public ResponseEntity<ApiResponse<AllocationSettingsResponse>> updateSettings(@Valid @RequestBody AllocationSettingsRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Settings saved", AllocationSettingsResponse.of(
                configService.updateSettings(tenant(), req.enabled(), req.defaultStrategy(), req.reallocateUnavailable()))));
    }

    @GetMapping("/collectors")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Collectors with their live load: open cases, portfolio value, open/overdue work items, utilisation")
    public ResponseEntity<ApiResponse<List<CollectorResponse>>> collectors() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                allocationService.collectorLoads(tenant()).stream().map(CollectorResponse::of).toList()));
    }

    @PutMapping("/collectors/{userId}")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Register or update a collector (team, tier, skills, limits, leave)")
    public ResponseEntity<ApiResponse<CollectorResponse>> upsertCollector(@PathVariable UUID userId,
                                                                          @Valid @RequestBody UpsertCollectorRequest req) {
        featureGuard.requireModule("debtcollection");
        var p = profileService.upsert(tenant(), userId, req.userName(), req.team(), req.tier(), req.skills(),
                req.maxOpenCases(), req.maxCaseValue(), req.maxPortfolioValue(), req.awayUntil());
        return ResponseEntity.ok(ApiResponse.success("Collector saved", CollectorResponse.of(p)));
    }

    @PostMapping("/collectors/{userId}/deactivate")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Stop allocating to this collector (their existing cases stay until reallocated)")
    public ResponseEntity<ApiResponse<CollectorResponse>> deactivate(@PathVariable UUID userId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Collector deactivated", CollectorResponse.of(profileService.setActive(tenant(), userId, false))));
    }

    @PostMapping("/collectors/{userId}/activate")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<CollectorResponse>> activate(@PathVariable UUID userId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Collector activated", CollectorResponse.of(profileService.setActive(tenant(), userId, true))));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<AllocationRuleResponse>>> rules() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                configService.listRules(tenant()).stream().map(AllocationRuleResponse::of).toList()));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<AllocationRuleResponse>> createRule(@Valid @RequestBody AllocationRuleRequest req) {
        featureGuard.requireModule("debtcollection");
        AllocationRule r = configService.createRule(tenant(), req.name(), req.ruleOrder(), req.minBalance(), req.maxBalance(),
                req.minAgeDays(), req.maxAgeDays(), req.statuses(), req.requiredTeam(), req.requiredSkill(), req.minTier(), req.strategy());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rule created", AllocationRuleResponse.of(r)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<AllocationRuleResponse>> updateRule(@PathVariable UUID id,
                                                                          @Valid @RequestBody AllocationRuleRequest req) {
        featureGuard.requireModule("debtcollection");
        AllocationRule r = configService.updateRule(tenant(), id, req.name(), req.ruleOrder(), req.minBalance(), req.maxBalance(),
                req.minAgeDays(), req.maxAgeDays(), req.statuses(), req.requiredTeam(), req.requiredSkill(), req.minTier(), req.strategy());
        return ResponseEntity.ok(ApiResponse.success("Rule updated", AllocationRuleResponse.of(r)));
    }

    @PostMapping("/rules/{id}/deactivate")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<AllocationRuleResponse>> deactivateRule(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Rule deactivated", AllocationRuleResponse.of(configService.setRuleActive(tenant(), id, false))));
    }

    @PostMapping("/rules/{id}/activate")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<AllocationRuleResponse>> activateRule(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Rule activated", AllocationRuleResponse.of(configService.setRuleActive(tenant(), id, true))));
    }

    private TenantId tenant() {
        return TenantContext.getTenantIdAsObject();
    }
}
