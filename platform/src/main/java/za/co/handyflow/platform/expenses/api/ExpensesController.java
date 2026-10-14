package za.co.handyflow.platform.expenses.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.expenses.application.internal.ExpensesService;
import za.co.handyflow.platform.expenses.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Staff expense claims with approval workflow")
public class ExpensesController {

    private final ExpensesService expensesService;

    /*
     * Finance patch F1 (2026-10-13). Before this, every read needed only USER_READ (anyone saw every claim) and approval needed
     * USER_UPDATE with no check against approving your own claim. Now:
     *   submit, list and read your own claims, add receipts to them: USER_CREATE / USER_READ as before;
     *   see everyone's claims: EXPENSES_READ;  approve or reject: EXPENSES_MANAGE (never your own claim);
     *   mark reimbursed (paying out is separate from approving): EXPENSES_ADMIN.
     * V383 grants the new permissions so the people who approve today still can.
     */
    private static boolean holds(String authority) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List expense claims with optional status and employee filters")
    public ResponseEntity<ApiResponse<Page<ExpenseClaimResponse>>> getClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID employeeId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                expensesService.getClaimsFor(TenantContext.getTenantIdAsObject(),
                        status, employeeId, pageable, holds("EXPENSES_READ"), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get expense claim detail")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> getClaim(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                expensesService.getClaimFor(TenantContext.getTenantIdAsObject(), id, holds("EXPENSES_READ"), TenantContext.getCurrentUserId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")   // FIXED: was USER_READ
    @Operation(summary = "Make an expense claim: sent for approval, or saved as a draft with ?draft=true")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> submitClaim(
            @Valid @RequestBody CreateExpenseClaimRequest req,
            @RequestParam(defaultValue = "false") boolean draft) {
        return ResponseEntity.status(201).body(ApiResponse.success(draft ? "Draft saved" : "Claim submitted",
                expensesService.submitClaim(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req, draft)));
    }

    // ── Finance patch F7: drafts, resubmission and withdrawal (the claimant only) ──

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Change a draft or rejected claim (the claimant only)")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> updateClaim(
            @PathVariable UUID id, @Valid @RequestBody CreateExpenseClaimRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Claim updated",
                expensesService.updateClaim(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Send a draft, or a corrected rejected claim, for approval (the claimant only)")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> submitExisting(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Claim submitted",
                expensesService.submitExisting(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Withdraw a draft or a claim waiting for approval (the claimant only)")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> withdrawClaim(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Claim withdrawn",
                expensesService.withdrawClaim(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('EXPENSES_MANAGE')")
    @Operation(summary = "Approve an expense claim — auto-posts journal entry to Accounting")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> approveClaim(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Claim approved",
                expensesService.approveClaim(TenantContext.getTenantIdAsObject(),
                        id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('EXPENSES_MANAGE')")
    @Operation(summary = "Reject an expense claim with a reason")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> rejectClaim(
            @PathVariable UUID id,
            @RequestBody RejectExpenseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Claim rejected",
                expensesService.rejectClaim(TenantContext.getTenantIdAsObject(),
                        id, TenantContext.getCurrentUserId(), req.reason())));
    }

    @GetMapping("/summary/monthly")
    @PreAuthorize("hasAuthority('EXPENSES_READ')")
    @Operation(summary = "Get total approved expenses for a month")
    public ResponseEntity<ApiResponse<BigDecimal>> getMonthlyTotal(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        int m = month == 0 ? LocalDate.now().getMonthValue() : month;
        int y = year  == 0 ? LocalDate.now().getYear()       : year;
        return ResponseEntity.ok(ApiResponse.success("Success",
                expensesService.getMonthlyTotal(
                        TenantContext.getTenantIdAsObject(), m, y)));
    }

    @PostMapping("/{id}/reimburse")
    @PreAuthorize("hasAuthority('EXPENSES_ADMIN')")
    @Operation(summary = "Pay an approved claim out and post the payment (Dr Accrued Expenses, Cr the bank account it was paid from)")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> markReimbursed(
            @PathVariable UUID id, @RequestBody(required = false) ReimburseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Claim reimbursed",
                expensesService.markReimbursed(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        req != null ? req.bankAccountId() : null, req != null ? req.reference() : null)));
    }

    public record ReimburseRequest(UUID bankAccountId, String reference) {}

    @PostMapping(value = "/{id}/receipt", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('USER_CREATE','EXPENSES_MANAGE')")
    @Operation(summary = "Attach a real receipt file to an existing claim")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.evidence.dto.EvidenceResponse>> attachReceipt(
            @PathVariable UUID id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        expensesService.requireAccess(TenantContext.getTenantIdAsObject(), id, holds("EXPENSES_MANAGE"), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Receipt attached",
              expensesService.attachReceipt(TenantContext.getTenantIdAsObject(), id, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List real receipt files attached to a claim")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getReceipts(
            @PathVariable UUID id) {
        expensesService.requireAccess(TenantContext.getTenantIdAsObject(), id, holds("EXPENSES_READ"), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(
                expensesService.getReceipts(TenantContext.getTenantIdAsObject(), id)));
    }
}