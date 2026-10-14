package za.co.handyflow.platform.expenses.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.expenses.application.internal.ExpensePolicyService;
import za.co.handyflow.platform.expenses.application.internal.ReimbursementBatchService;
import za.co.handyflow.platform.expenses.dto.ExpensePolicyDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Expense policies and reimbursement batches (finance F18). */
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expense policies and reimbursement batches")
public class ExpensePolicyController {

    private final ExpensePolicyService policies;
    private final ReimbursementBatchService batches;

    @GetMapping("/policies")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','EXPENSES_READ','EXPENSES_ADMIN')")
    @Operation(summary = "Limits and receipt rules, so claimants can see them before submitting")
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> listPolicies() {
        return ResponseEntity.ok(ApiResponse.success("Success", policies.list(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/policies")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    @Operation(summary = "Set the policy for a category (or '*' for all others)")
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> savePolicy(@RequestBody PolicyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Policy saved",
                policies.save(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> deletePolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Policy removed", policies.delete(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/reimbursements/owed")
    @PreAuthorize("hasAuthority('EXPENSES_READ')")
    @Operation(summary = "Approved claims not yet paid, by employee")
    public ResponseEntity<ApiResponse<List<OwedToEmployee>>> owed() {
        return ResponseEntity.ok(ApiResponse.success("Success", batches.owed(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/reimbursements/batches")
    @PreAuthorize("hasAuthority('EXPENSES_READ')")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> listBatches() {
        return ResponseEntity.ok(ApiResponse.success("Success", batches.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/reimbursements/batches")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    @Operation(summary = "Group approved claims into a batch to pay together")
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(@RequestBody CreateBatchRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Batch created",
                batches.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/reimbursements/batches/{id}/pay")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    @Operation(summary = "Pay every claim in the batch under one reference; all or nothing")
    public ResponseEntity<ApiResponse<BatchResponse>> payBatch(@PathVariable UUID id, @RequestBody PayBatchRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Batch paid",
                batches.pay(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/reimbursements/batches/{id}/cancel")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    public ResponseEntity<ApiResponse<BatchResponse>> cancelBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Batch cancelled", batches.cancel(TenantContext.getTenantIdAsObject(), id)));
    }
}
