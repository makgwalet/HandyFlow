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

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List expense claims with optional status and employee filters")
    public ResponseEntity<ApiResponse<Page<ExpenseClaimResponse>>> getClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID employeeId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                expensesService.getClaims(TenantContext.getTenantIdAsObject(),
                        status, employeeId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get expense claim detail")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> getClaim(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                expensesService.getClaim(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")   // FIXED: was USER_READ
    @Operation(summary = "Submit a new expense claim")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> submitClaim(
            @Valid @RequestBody CreateExpenseClaimRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Claim submitted",
                expensesService.submitClaim(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")   // FIXED: was USER_READ
    @Operation(summary = "Approve an expense claim — auto-posts journal entry to Accounting")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> approveClaim(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Claim approved",
                expensesService.approveClaim(TenantContext.getTenantIdAsObject(),
                        id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('USER_UPDATE')")   // FIXED: was USER_READ
    @Operation(summary = "Reject an expense claim with a reason")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> rejectClaim(
            @PathVariable UUID id,
            @RequestBody RejectExpenseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Claim rejected",
                expensesService.rejectClaim(TenantContext.getTenantIdAsObject(),
                        id, TenantContext.getCurrentUserId(), req.reason())));
    }

//    @PostMapping("/{id}/reimburse")
//    @PreAuthorize("hasAuthority('USER_UPDATE')")   // FIXED: was USER_READ
//    @Operation(summary = "Mark an approved claim as reimbursed")
//    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> markReimbursed(
//            @PathVariable UUID id) {
//        return ResponseEntity.ok(ApiResponse.success("Claim reimbursed",
//                expensesService.markReimbursed(TenantContext.getTenantIdAsObject(), id)));
//    }

    @GetMapping("/summary/monthly")
    @PreAuthorize("hasAuthority('USER_READ')")
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
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Mark an approved claim as reimbursed")
    public ResponseEntity<ApiResponse<ExpenseClaimResponse>> markReimbursed(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Claim reimbursed",
                expensesService.markReimbursed(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping(value = "/{id}/receipt", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Attach a real receipt file to an existing claim")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.evidence.dto.EvidenceResponse>> attachReceipt(
            @PathVariable UUID id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Receipt attached",
              expensesService.attachReceipt(TenantContext.getTenantIdAsObject(), id, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List real receipt files attached to a claim")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getReceipts(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                expensesService.getReceipts(TenantContext.getTenantIdAsObject(), id)));
    }
}