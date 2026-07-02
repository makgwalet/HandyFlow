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
import za.co.handyflow.platform.projects.application.internal.ChangeOrderService;
import za.co.handyflow.platform.projects.application.internal.ResourceService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resources, budget lines, EVM and change orders.
 *
 * CHANGES FROM ORIGINAL
 * ──────────────────────
 * 1. @Validated on class + @Valid on @RequestBody parameters.
 *
 * 2. assignResource() now returns { resource, warnings } instead of just resource.
 *    When a double-booking is detected, the response body contains a warnings array
 *    so the frontend can alert the manager without blocking the save.
 *
 * 3. updateBudgetLine() uses a typed DTO instead of Map<String,Object>.
 *    Receiving Map<String,Object> forces manual type-casting (fragile and ugly).
 *    A typed DTO with Bean Validation is safer and self-documenting.
 *
 * 4. EVM endpoint: removed requirement for caller to pass planPct.
 *    BudgetService now auto-computes planPct from project dates when not supplied.
 *    planPct still accepted as optional override.
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
    private final ChangeOrderService changeOrderService;

    // ── Resources ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List resources assigned to a project")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getResources(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                resourceService.getResources(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(ResourceResponse::of).toList()));
    }

    /**
     * Assigns a resource and returns both the assignment AND any double-booking
     * warnings in a single response body.
     *
     * Response shape (when conflicts exist):
     * {
     *   "success": true,
     *   "message": "Resource assigned — 1 scheduling conflict detected",
     *   "data": {
     *     "resource": { ... },
     *     "warnings": ["Resource 'Thabo' is already assigned to project PRJ0003 ..."]
     *   }
     * }
     *
     * The frontend should check data.warnings.length > 0 and show a toast.
     */
    @PostMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Assign a resource — returns warnings on double-booking (never blocks)")
    public ResponseEntity<ApiResponse<ResourceAssignmentResponse>> assignResource(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateResourceRequest req) {

        AssignResourceResult result = resourceService.assignResource(
                TenantContext.getTenantIdAsObject(), projectId, req);

        String message = result.hasConflicts()
                ? "Resource assigned — " + result.warnings().size() + " scheduling conflict(s) detected"
                : "Resource assigned";

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message,
                        new ResourceAssignmentResponse(
                                ResourceResponse.of(result.resource()),
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
    public ResponseEntity<ApiResponse<List<BudgetLineResponse>>> getBudget(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.getBudgetLines(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(BudgetLineResponse::of).toList()));
    }

    @PostMapping("/{projectId}/budget")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Add a budget line — updates project.budget_total automatically")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> addBudgetLine(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateBudgetLineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Budget line added",
                        BudgetLineResponse.of(budgetService.createBudgetLine(
                                TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    /**
     * FIX: was Map<String,Object> — fragile, forces manual type-casting.
     * Now uses a typed DTO: UpdateBudgetLineRequest { budgetedAmount?, description? }
     */
    @PutMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Update a budget line amount or description")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> updateBudgetLine(
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateBudgetLineRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Budget line updated",
                BudgetLineResponse.of(budgetService.updateBudgetLine(
                        TenantContext.getTenantIdAsObject(), lineId,
                        req.budgetedAmount(), req.description()))));
    }

    @DeleteMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Delete a budget line — recalculates project budget total")
    public ResponseEntity<ApiResponse<Void>> deleteBudgetLine(@PathVariable UUID lineId) {
        budgetService.deleteBudgetLine(TenantContext.getTenantIdAsObject(), lineId);
        return ResponseEntity.ok(ApiResponse.success("Budget line deleted", null));
    }

    /**
     * EVM endpoint.
     * planPct is now optional (defaults to 0 = auto-compute from project dates).
     * earnedPct should be the weighted task completion percentage.
     *
     * FIX: BudgetService.getEvm() now computes planPct from actual schedule
     * when the caller passes 0 or omits it, instead of relying on the frontend's
     * hardcoded planPct=50 which made SPI meaningless.
     */
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

    // ── Change orders ─────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/change-orders")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List change orders for a project")
    public ResponseEntity<ApiResponse<List<ChangeOrderResponse>>> getChangeOrders(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                changeOrderService.getChangeOrders(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(ChangeOrderResponse::of).toList()));
    }

    @PostMapping("/{projectId}/change-orders")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a change order — auto-assigns CO number, starts in DRAFT")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> createChangeOrder(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateChangeOrderRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Change order created",
                        ChangeOrderResponse.of(changeOrderService.createChangeOrder(
                                TenantContext.getTenantIdAsObject(), projectId, req, userId))));
    }

    @PostMapping("/change-orders/{id}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Submit CO for approval (DRAFT → SUBMITTED)")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> submitCO(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted",
                ChangeOrderResponse.of(changeOrderService.submitChangeOrder(
                        TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/change-orders/{id}/approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Approve CO — extends end date AND creates CONTINGENCY budget line")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> approveCO(@PathVariable UUID id) {
        UUID userId = TenantContext.getCurrentUserId();
        String name = TenantContext.getCurrentUserName();  // FIX: use real name, not UUID.toString()
        return ResponseEntity.ok(ApiResponse.success("Change order approved",
                ChangeOrderResponse.of(changeOrderService.approveChangeOrder(
                        TenantContext.getTenantIdAsObject(), id, userId, name))));
    }

    @PostMapping("/change-orders/{id}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Reject a submitted CO with a reason")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> rejectCO(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Change order rejected",
                ChangeOrderResponse.of(changeOrderService.rejectChangeOrder(
                        TenantContext.getTenantIdAsObject(), id, body.get("reason")))));
    }

    @PostMapping("/change-orders/{id}/client-approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Record client sign-off on an approved change order")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> clientApproveCO(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Client approval recorded",
                ChangeOrderResponse.of(changeOrderService.markClientApproved(
                        TenantContext.getTenantIdAsObject(), id))));
    }
}
