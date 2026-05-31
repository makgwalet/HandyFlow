package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.AccountingService;
import za.co.handyflow.platform.accounting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Tag(name = "Accounting", description = "Chart of accounts, journal entries, bank accounts and financial reports")
public class AccountingController {

    private final AccountingService accountingService;

    // ── Chart of Accounts ─────────────────────────────────────────────────────

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get chart of accounts (seeds standard SA accounts on first call)")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccounts() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getAccounts(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/accounts/type/{type}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get accounts by type: ASSET | LIABILITY | EQUITY | INCOME | EXPENSE")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByType(
            @PathVariable String type) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getAccountsByType(TenantContext.getTenantIdAsObject(), type)));
    }

    // ── Journal Entries ───────────────────────────────────────────────────────

    @GetMapping("/journal-entries")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List journal entries, optionally filter by status (DRAFT | POSTED)")
    public ResponseEntity<ApiResponse<Page<JournalEntryResponse>>> getJournalEntries(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getJournalEntries(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/journal-entries")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a double-entry journal entry (must balance: debits = credits)")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> createJournalEntry(
            @Valid @RequestBody CreateJournalEntryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Journal entry created",
                accountingService.createJournalEntry(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/journal-entries/{id}/post")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Post a DRAFT journal entry (locks it — cannot be edited after posting)")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> postJournalEntry(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Journal entry posted",
                accountingService.postJournalEntry(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Bank Accounts ─────────────────────────────────────────────────────────

    @GetMapping("/bank-accounts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List bank accounts")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> getBankAccounts() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getBankAccounts(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/bank-accounts")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Register a bank account")
    public ResponseEntity<ApiResponse<BankAccountResponse>> createBankAccount(
            @Valid @RequestBody CreateBankAccountRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Bank account added",
                accountingService.createBankAccount(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/bank-accounts/{id}/transactions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Add a bank transaction (CREDIT = money in, DEBIT = money out)")
    public ResponseEntity<ApiResponse<BankAccountResponse>> addTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody AddBankTransactionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Transaction recorded",
                accountingService.addTransaction(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Financial Reports ─────────────────────────────────────────────────────

    @GetMapping("/reports/profit-and-loss")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Profit & Loss statement for a date range")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getProfitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getProfitAndLoss(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/reports/trial-balance")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Trial balance for a date range")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getTrialBalance(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/reports/balance-sheet")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Balance sheet as at a date")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getBalanceSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getBalanceSheet(TenantContext.getTenantIdAsObject(), from, to)));
    }
}