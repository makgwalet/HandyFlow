package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.BudgetService;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ResourceService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resources, budget lines and EVM.
 * CHUNK 5: change-order endpoints moved to ChangeOrderController (same URLs, plus new ones).
 *
 * CHUNK 2b: the resource endpoints (visible to every project member) omit hourly /
 * daily pay rates for roles without cost visibility. The budget, EVM and
 * change-order endpoints need no masking here — ProjectAccessInterceptor already
 * refuses those routes entirely to roles without the cost capability.
 *
 * assignResource() returns { resource, warnings } — a double-booking is a warning,
 * never a block (managers may override).
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Resources, Budget & Change Orders",
        description = "Resource assignment, budget lines, EVM, change orders")
public class ResourceController {

    private final ResourceService    resourceService;
    private final BudgetService      budgetService;
    private final ProjectAccessGuard accessGuard;

    // ── Resources ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List resources assigned to a project (pay rates hidden without cost access)")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getResources(@PathVariable UUID projectId) {
        boolean cost = canViewCost(projectId);
        return ResponseEntity.ok(ApiResponse.success("Success",
                resourceService.getResources(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(r -> view(ResourceResponse.of(r), cost)).toList()));
    }

    @PostMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Assign a resource — returns warnings on double-booking (never blocks)")
    public ResponseEntity<ApiResponse<ResourceAssignmentResponse>> assignResource(
            @PathVariable UUID projectId, @Valid @RequestBody CreateResourceRequest req) {

        AssignResourceResult result = resourceService.assignResource(
                TenantContext.getTenantIdAsObject(), projectId, req);

        String message = result.hasConflicts()
                ? "Resource assigned — " + result.warnings().size() + " scheduling conflict(s) detected"
                : "Resource assigned";

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message,
                        new ResourceAssignmentResponse(
                                view(ResourceResponse.of(result.resource()), canViewCost(projectId)),
                                result.warnings())));
    }

    @DeleteMapping("/resources/{resourceId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Remove a resource assignment")
    public ResponseEntity<ApiResponse<Void>> removeResource(@PathVariable UUID resourceId) {
        resourceService.removeResource(TenantContext.getTenantIdAsObject(), resourceId);
        return ResponseEntity.ok(ApiResponse.success("Resource removed", null));
    }

    // ── Budget lines ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/budget")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Budget lines for a project — breakdown by category")
    public ResponseEntity<ApiResponse<List<BudgetLineResponse>>> getBudget(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.getBudgetLines(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(BudgetLineResponse::of).toList()));
    }

    @PostMapping("/{projectId}/budget")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Add a budget line — updates project.budget_total automatically")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> addBudgetLine(
            @PathVariable UUID projectId, @Valid @RequestBody CreateBudgetLineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Budget line added",
                        BudgetLineResponse.of(budgetService.createBudgetLine(
                                TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PutMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Update a budget line amount or description")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> updateBudgetLine(
            @PathVariable UUID lineId, @Valid @RequestBody UpdateBudgetLineRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Budget line updated",
                BudgetLineResponse.of(budgetService.updateBudgetLine(
                        TenantContext.getTenantIdAsObject(), lineId,
                        req.budgetedAmount(), req.description(), req.reason()))));
    }

    @DeleteMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Delete a budget line — recalculates project budget total")
    public ResponseEntity<ApiResponse<Void>> deleteBudgetLine(@PathVariable UUID lineId) {
        budgetService.deleteBudgetLine(TenantContext.getTenantIdAsObject(), lineId);
        return ResponseEntity.ok(ApiResponse.success("Budget line deleted", null));
    }

    /** planPct is optional (0 = auto-compute from project dates); earnedPct is weighted task completion %. */
    @GetMapping("/{projectId}/budget/evm")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "EVM — SPI, CPI, EAC, ETC. planPct is auto-computed if omitted.")
    public ResponseEntity<ApiResponse<BudgetSummaryResponse>> getEvm(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") BigDecimal planPct,
            @RequestParam(defaultValue = "0") BigDecimal earnedPct) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.getEvm(TenantContext.getTenantIdAsObject(),
                        projectId, planPct, earnedPct)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean canViewCost(UUID projectId) {
        return accessGuard.accessFor(TenantContext.getTenantIdAsObject().getValue(), projectId).cost();
    }

    private static ResourceResponse view(ResourceResponse r, boolean cost) {
        return cost ? r : r.withoutRates();
    }
}