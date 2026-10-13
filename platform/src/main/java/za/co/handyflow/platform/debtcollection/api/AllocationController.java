package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationService;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationService.ApplyResult;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationService.Plan;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationScope;
import za.co.handyflow.platform.debtcollection.dto.ManualAssignRequest;
import za.co.handyflow.platform.debtcollection.dto.ManualAssignResponse;
import za.co.handyflow.platform.debtcollection.dto.RunAllocationRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Running allocation. PREVIEW changes nothing and shows the plan (who gets which case and why, plus cases nobody is
 * eligible for). APPLY (supervisor) recomputes and applies it case by case with per-case outcomes. Manual assignment is
 * capacity-checked; only a supervisor can override, with a reason.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/allocation")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Allocation", description = "Preview and apply case allocation; capacity-checked manual assignment")
public class AllocationController {

    private final AllocationService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/preview")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Dry run: the plan for UNASSIGNED / UNAVAILABLE / FROM_USER (+fromUserId). Nothing is changed")
    public ResponseEntity<ApiResponse<Plan>> preview(@RequestParam(defaultValue = "UNASSIGNED") AllocationScope scope,
                                                     @RequestParam(required = false) UUID fromUserId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(service.plan(tenant(), scope, fromUserId)));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Apply an allocation run; returns the plan plus applied / skipped / failed counts and per-case problems")
    public ResponseEntity<ApiResponse<ApplyResult>> apply(@Valid @RequestBody RunAllocationRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Allocation applied", service.apply(tenant(), req.scope(), req.fromUserId())));
    }

    @PostMapping("/cases/{caseId}/assign")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Assign one case with a capacity/availability check; work items follow. override needs DEBTCOLLECTION_ADMIN + reason")
    public ResponseEntity<ApiResponse<ManualAssignResponse>> assign(@PathVariable UUID caseId,
                                                                    @Valid @RequestBody ManualAssignRequest req) {
        featureGuard.requireModule("debtcollection");
        if (req.override() && !isAdmin()) {
            throw new AccessDeniedException("Only a supervisor (DEBTCOLLECTION_ADMIN) can override capacity limits");
        }
        return ResponseEntity.ok(ApiResponse.success("Case assigned", ManualAssignResponse.of(
                service.assignManually(tenant(), caseId, req.userId(), req.override(), req.reason()))));
    }

    @PostMapping("/reconcile-work-items")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Make live work items follow their case's current collector (also runs automatically with auto-allocation)")
    public ResponseEntity<ApiResponse<Integer>> reconcile() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Work items reconciled", service.reconcileWorkItems(tenant())));
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "DEBTCOLLECTION_ADMIN".equals(a.getAuthority()));
    }

    private TenantId tenant() {
        return TenantContext.getTenantIdAsObject();
    }
}
