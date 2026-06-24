package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Resources, Budget & Change Orders", description = "Resource assignment, budget lines, EVM, change orders")
public class ResourceController {

    private final ResourceService     resourceService;
    private final BudgetService       budgetService;
    private final ChangeOrderService  changeOrderService;

    // ── Resources ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List resources assigned to a project — humans, equipment, vehicles, subcontractors")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getResources(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                resourceService.getResources(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(ResourceResponse::of).toList()));
    }

    @PostMapping("/{projectId}/resources")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Assign a resource to a project (or task). Checks for double-booking on HUMAN resources.")
    public ResponseEntity<ApiResponse<ResourceResponse>> assignResource(
            @PathVariable UUID projectId, @RequestBody CreateResourceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Resource assigned",
                ResourceResponse.of(resourceService.assignResource(
                        TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @DeleteMapping("/resources/{resourceId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeResource(@PathVariable UUID resourceId) {
        resourceService.removeResource(TenantContext.getTenantIdAsObject(), resourceId);
        return ResponseEntity.ok(ApiResponse.success("Resource removed", null));
    }

    // ── Budget lines ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/budget")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Budget lines for a project — breakdown by category (LABOUR, MATERIALS, etc.)")
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
            @PathVariable UUID projectId, @RequestBody CreateBudgetLineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Budget line added",
                BudgetLineResponse.of(budgetService.createBudgetLine(
                        TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PutMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> updateBudgetLine(
            @PathVariable UUID lineId, @RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("budgetedAmount") != null
                ? new BigDecimal(body.get("budgetedAmount").toString()) : null;
        String description = (String) body.get("description");
        return ResponseEntity.ok(ApiResponse.success("Budget line updated",
                BudgetLineResponse.of(budgetService.updateBudgetLine(
                        TenantContext.getTenantIdAsObject(), lineId, amount, description))));
    }

    @DeleteMapping("/budget/{lineId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBudgetLine(@PathVariable UUID lineId) {
        budgetService.deleteBudgetLine(TenantContext.getTenantIdAsObject(), lineId);
        return ResponseEntity.ok(ApiResponse.success("Budget line deleted", null));
    }

    @GetMapping("/{projectId}/budget/evm")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Earned Value Management — SPI, CPI, EAC, ETC. Pass planPct and earnedPct as query params.")
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
    public ResponseEntity<ApiResponse<List<ChangeOrderResponse>>> getChangeOrders(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                changeOrderService.getChangeOrders(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(ChangeOrderResponse::of).toList()));
    }

    @PostMapping("/{projectId}/change-orders")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a change order — auto-assigns CO-number, starts in DRAFT")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> createChangeOrder(
            @PathVariable UUID projectId, @RequestBody CreateChangeOrderRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Change order created",
                ChangeOrderResponse.of(changeOrderService.createChangeOrder(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId))));
    }

    @PostMapping("/change-orders/{id}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> submitCO(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted",
                ChangeOrderResponse.of(changeOrderService.submitChangeOrder(
                        TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/change-orders/{id}/approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Approve CO — extends project end date if schedule_impact > 0")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> approveCO(@PathVariable UUID id) {
        UUID userId = TenantContext.getCurrentUserId();
        String name = userId != null ? userId.toString() : "approver";
        return ResponseEntity.ok(ApiResponse.success("Change order approved",
                ChangeOrderResponse.of(changeOrderService.approveChangeOrder(
                        TenantContext.getTenantIdAsObject(), id, userId, name))));
    }

    @PostMapping("/change-orders/{id}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> rejectCO(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Change order rejected",
                ChangeOrderResponse.of(changeOrderService.rejectChangeOrder(
                        TenantContext.getTenantIdAsObject(), id, body.get("reason")))));
    }

    @PostMapping("/change-orders/{id}/client-approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Record client sign-off on an approved change order")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> clientApproveCO(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Client approval recorded",
                ChangeOrderResponse.of(changeOrderService.markClientApproved(
                        TenantContext.getTenantIdAsObject(), id))));
    }
}
