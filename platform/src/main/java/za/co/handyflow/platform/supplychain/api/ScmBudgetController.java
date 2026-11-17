package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.BudgetService;
import za.co.handyflow.platform.supplychain.domain.model.ScBudgetOverride;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Procurement budgets, commitments and overrides. Fine-grained authorities work alongside SCM_ADMIN. */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Procurement budgets and commitments")
public class ScmBudgetController {

    private final BudgetService service;

    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_BUDGET_MANAGE','SCM_ADMIN')";
    private static final String CAN_READ = "hasAnyAuthority('SCM_BUDGET_READ','SCM_READ','SCM_ADMIN')";
    private static final String CAN_OVERRIDE = "hasAnyAuthority('SCM_BUDGET_OVERRIDE','SCM_ADMIN')";

    @PostMapping("/budgets")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Create a budget for a department, cost centre or project over a period")
    public ResponseEntity<ApiResponse<BudgetResponse>> create(@RequestBody CreateBudgetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Budget created",
                service.create(tenant(), uid(), uname(), req)));
    }

    @PutMapping("/budgets/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Change a budget's name, amount or control mode, or close it — audited")
    public ResponseEntity<ApiResponse<BudgetResponse>> update(@PathVariable UUID id, @RequestBody UpdateBudgetRequest req) {
        return ok("Budget updated", service.update(tenant(), id, uid(), uname(), req));
    }

    @GetMapping("/budgets")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "All budgets with committed / actual / available")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> list() {
        return ok("Success", service.list(tenant()));
    }

    @GetMapping("/budgets/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<BudgetResponse>> get(@PathVariable UUID id) {
        return ok("Success", service.get(tenant(), id));
    }

    @GetMapping("/budgets/{id}/commitments")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "The purchase orders that make up a budget's usage")
    public ResponseEntity<ApiResponse<List<BudgetCommitmentResponse>>> commitments(@PathVariable UUID id) {
        return ok("Success", service.commitments(tenant(), id));
    }

    @GetMapping("/budgets/check")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "What-if: would this spend fit the applicable budgets? Changes nothing.")
    public ResponseEntity<ApiResponse<BudgetCheckResponse>> check(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String costCentre,
            @RequestParam(required = false) String projectRef,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ok("Success", service.preview(tenant(), department, costCentre, projectRef, amount, date));
    }

    @PostMapping("/budgets/backfill-commitments")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "One-off: create commitments for approved POs that predate budgets")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> backfill() {
        return ok("Backfill complete", Map.of("created", service.backfillCommitments(tenant(), uid(), uname())));
    }

    @PostMapping("/purchase-orders/{id}/budget-override")
    @PreAuthorize(CAN_OVERRIDE)
    @Operation(summary = "Authorise a PO to proceed despite a hard-stop budget (reason required, audited)")
    public ResponseEntity<ApiResponse<Void>> overridePo(@PathVariable UUID id, @RequestBody BudgetOverrideRequest req) {
        service.override(tenant(), ScBudgetOverride.PURCHASE_ORDER, id, req.reason(), uid(), uname());
        return ok("Budget override recorded", null);
    }

    @PostMapping("/requisitions/{id}/budget-override")
    @PreAuthorize(CAN_OVERRIDE)
    @Operation(summary = "Authorise a requisition to proceed despite a hard-stop budget (reason required, audited)")
    public ResponseEntity<ApiResponse<Void>> overrideRequisition(@PathVariable UUID id, @RequestBody BudgetOverrideRequest req) {
        service.override(tenant(), ScBudgetOverride.REQUISITION, id, req.reason(), uid(), uname());
        return ok("Budget override recorded", null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T d) { return ResponseEntity.ok(ApiResponse.success(m, d)); }
    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
