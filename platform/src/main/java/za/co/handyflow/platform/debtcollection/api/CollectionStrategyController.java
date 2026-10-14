package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.CollectionStrategyEngine;
import za.co.handyflow.platform.debtcollection.application.internal.CollectionStrategyService;
import za.co.handyflow.platform.debtcollection.dto.StrategyDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Collection strategies: when to call, remind, demand or review, by balance and days overdue (finance F23). */
@RestController
@RequestMapping("/api/v1/debtcollection/strategies")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Strategies")
public class CollectionStrategyController {

    private static final String READ = "hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')";
    private static final String ADMIN = "hasAuthority('DEBTCOLLECTION_ADMIN')";

    private final CollectionStrategyService service;
    private final CollectionStrategyEngine engine;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<StrategyResponse>>> list() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<StrategyResponse>> create(@RequestBody StrategyRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Strategy saved", service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<StrategyResponse>> update(@PathVariable UUID id, @RequestBody StrategyRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Strategy saved", service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/active")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<StrategyResponse>> active(@PathVariable UUID id, @RequestBody ActiveRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Updated", service.setActive(TenantContext.getTenantIdAsObject(), id, req.active())));
    }

    @GetMapping("/preview")
    @PreAuthorize(READ)
    @Operation(summary = "What the active strategies would do today. Changes nothing.")
    public ResponseEntity<ApiResponse<RunResult>> preview() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Success", engine.preview(TenantContext.getTenantIdAsObject(), LocalDate.now())));
    }

    @PostMapping("/run")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Raise today's work items now (the daily job does this too). Sends nothing to debtors.")
    public ResponseEntity<ApiResponse<RunResult>> run() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Done", engine.apply(TenantContext.getTenantIdAsObject(), LocalDate.now())));
    }

    @GetMapping("/cases/{caseId}")
    @PreAuthorize(READ)
    @Operation(summary = "The strategy a case falls under, what comes next, and what was raised before")
    public ResponseEntity<ApiResponse<CaseStrategy>> forCase(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Success", engine.forCase(TenantContext.getTenantIdAsObject(), caseId, LocalDate.now())));
    }
}
