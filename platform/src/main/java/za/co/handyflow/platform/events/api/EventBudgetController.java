package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.EventBudgetService;
import za.co.handyflow.platform.events.application.internal.EventSupplierCostService;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Event budget, supplier commitments and cost tracking. Separate from
 * EventsController (lifecycle) and EventRegistrationController (guests);
 * all three share the /api/v1/events base path.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event budget", description = "Budget, approval, supplier commitments and cost tracking")
public class EventBudgetController {

    private final EventBudgetService       budgetService;
    private final EventSupplierCostService supplierCostService;

    // ── Budget & approval ─────────────────────────────────────────────────────

    @GetMapping("/{id}/budget")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Budget status — NOT_STARTED, DRAFT, SUBMITTED or APPROVED — with planned total")
    public ResponseEntity<ApiResponse<BudgetStatusResponse>> getBudget(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.getStatus(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/budget/summary")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Planned vs committed vs invoiced vs paid, per line and in total, with forecast and alerts")
    public ResponseEntity<ApiResponse<BudgetSummaryResponse>> getSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.getSummary(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/budget/lines")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "List budget lines")
    public ResponseEntity<ApiResponse<List<BudgetLineResponse>>> getLines(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                budgetService.listLines(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/budget/lines")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Add a budget line — only while the budget is DRAFT")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> addLine(
            @PathVariable UUID id, @Valid @RequestBody BudgetLineRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Budget line added",
                budgetService.addLine(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/budget/lines/{lineId}")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Update a budget line — only while the budget is DRAFT")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> updateLine(
            @PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody BudgetLineRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Budget line updated",
                budgetService.updateLine(TenantContext.getTenantIdAsObject(), id, lineId, req)));
    }

    @DeleteMapping("/{id}/budget/lines/{lineId}")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Delete a budget line — only while DRAFT and nothing refers to it")
    public ResponseEntity<ApiResponse<Void>> deleteLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        budgetService.deleteLine(TenantContext.getTenantIdAsObject(), id, lineId);
        return ResponseEntity.ok(ApiResponse.success("Budget line deleted", null));
    }

    @PostMapping("/{id}/budget/submit")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Submit the budget for approval — locks its lines")
    public ResponseEntity<ApiResponse<BudgetStatusResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Budget submitted",
                budgetService.submit(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/budget/approve")
    @PreAuthorize(EventsPermissions.BUDGET_APPROVE)
    @Operation(summary = "Approve a submitted budget — supplier commitments can then be approved against it")
    public ResponseEntity<ApiResponse<BudgetStatusResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Budget approved",
                budgetService.approve(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/budget/reject")
    @PreAuthorize(EventsPermissions.BUDGET_APPROVE)
    @Operation(summary = "Send a submitted budget back for changes, with a reason")
    public ResponseEntity<ApiResponse<BudgetStatusResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody BudgetReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Budget sent back",
                budgetService.reject(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/{id}/budget/reopen")
    @PreAuthorize(EventsPermissions.BUDGET_APPROVE)
    @Operation(summary = "Reopen a submitted or approved budget for editing — clears the approval; reason required")
    public ResponseEntity<ApiResponse<BudgetStatusResponse>> reopen(
            @PathVariable UUID id, @Valid @RequestBody BudgetReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Budget reopened",
                budgetService.reopen(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    // ── Supplier commitments ──────────────────────────────────────────────────

    @GetMapping("/{id}/commitments")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "List supplier quotes and commitments with invoiced and paid amounts")
    public ResponseEntity<ApiResponse<List<CommitmentResponse>>> getCommitments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                supplierCostService.listCommitments(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/budget/suppliers")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Per-supplier committed, invoiced, paid and outstanding")
    public ResponseEntity<ApiResponse<List<SupplierSummary>>> getSupplierSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                supplierCostService.supplierSummary(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/commitments")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Record a supplier quote against a budget line")
    public ResponseEntity<ApiResponse<CommitmentResponse>> createCommitment(
            @PathVariable UUID id, @Valid @RequestBody CommitmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Quote recorded",
                supplierCostService.createCommitment(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/commitments/{commitmentId}")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Update a commitment — a quote freely; once approved, only references, dates and deposit schedule")
    public ResponseEntity<ApiResponse<CommitmentResponse>> updateCommitment(
            @PathVariable UUID id, @PathVariable UUID commitmentId, @Valid @RequestBody CommitmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Commitment updated",
                supplierCostService.updateCommitment(TenantContext.getTenantIdAsObject(), id, commitmentId, req)));
    }

    @PostMapping("/{id}/commitments/{commitmentId}/approve")
    @PreAuthorize(EventsPermissions.BUDGET_APPROVE)
    @Operation(summary = "Approve a quote as a commitment — needs an approved budget; an override reason if it exceeds the line")
    public ResponseEntity<ApiResponse<CommitmentResponse>> approveCommitment(
            @PathVariable UUID id, @PathVariable UUID commitmentId,
            @Valid @RequestBody(required = false) ApproveCommitmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Commitment approved",
                supplierCostService.approveCommitment(TenantContext.getTenantIdAsObject(), id, commitmentId,
                        req != null ? req.overrideReason() : null)));
    }

    @PostMapping("/{id}/commitments/{commitmentId}/complete")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Mark a commitment delivered and closed — its final cost becomes what was invoiced")
    public ResponseEntity<ApiResponse<CommitmentResponse>> completeCommitment(
            @PathVariable UUID id, @PathVariable UUID commitmentId) {
        return ResponseEntity.ok(ApiResponse.success("Commitment completed",
                supplierCostService.completeCommitment(TenantContext.getTenantIdAsObject(), id, commitmentId)));
    }

    @PostMapping("/{id}/commitments/{commitmentId}/cancel")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Cancel a quote or commitment — void any invoices against it first")
    public ResponseEntity<ApiResponse<CommitmentResponse>> cancelCommitment(
            @PathVariable UUID id, @PathVariable UUID commitmentId,
            @Valid @RequestBody CancelCommitmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Commitment cancelled",
                supplierCostService.cancelCommitment(TenantContext.getTenantIdAsObject(), id, commitmentId, req.reason())));
    }

    // ── Cost entries (invoices & payments) ────────────────────────────────────

    @GetMapping("/{id}/cost-entries")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "List invoices and payments, newest first; filter by commitment, line, kind or status")
    public ResponseEntity<ApiResponse<Page<CostEntryResponse>>> getCostEntries(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID commitmentId,
            @RequestParam(required = false) UUID lineId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                supplierCostService.listEntries(TenantContext.getTenantIdAsObject(), id,
                        commitmentId, lineId, kind, status, pageable)));
    }

    @PostMapping("/{id}/cost-entries")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Record an invoice received or a payment made (allowed in any event status)")
    public ResponseEntity<ApiResponse<CostEntryResponse>> recordEntry(
            @PathVariable UUID id, @Valid @RequestBody CostEntryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Cost recorded",
                supplierCostService.recordEntry(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/cost-entries/{entryId}/void")
    @PreAuthorize(EventsPermissions.BUDGET_MANAGE)
    @Operation(summary = "Void a cost entry with a reason — entries are never deleted")
    public ResponseEntity<ApiResponse<CostEntryResponse>> voidEntry(
            @PathVariable UUID id, @PathVariable UUID entryId, @Valid @RequestBody VoidCostEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Cost entry voided",
                supplierCostService.voidEntry(TenantContext.getTenantIdAsObject(), id, entryId, req.reason())));
    }
}