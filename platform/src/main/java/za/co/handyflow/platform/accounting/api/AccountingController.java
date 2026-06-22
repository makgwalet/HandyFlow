package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.AccountingReportPdfService;
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
@Tag(name = "Accounting", description = "Chart of accounts, journals, bank accounts, VAT, reports")
public class AccountingController {

    private final AccountingService accountingService;
    private final AccountingReportPdfService reportPdfService;

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
    @Operation(summary = "List journal entries (optionally filter by status: DRAFT | POSTED | REVERSED)")
    public ResponseEntity<ApiResponse<Page<JournalEntryResponse>>> getJournalEntries(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getJournalEntries(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/journal-entries")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a double-entry journal entry (must balance: total debits = total credits)")
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

    @PostMapping("/journal-entries/{id}/reverse")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Reverse a POSTED journal entry — creates equal-and-opposite entry dated today (or supplied date)")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reverseJournalEntry(
            @PathVariable UUID id,
            @RequestBody(required = false) ReverseJournalRequest req) {
        LocalDate reversalDate = req != null ? req.reversalDate() : null;
        return ResponseEntity.ok(ApiResponse.success("Journal entry reversed",
                accountingService.reverseJournalEntry(TenantContext.getTenantIdAsObject(), id, reversalDate)));
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
    @Operation(summary = "Register a new bank account")
    public ResponseEntity<ApiResponse<BankAccountResponse>> createBankAccount(
            @Valid @RequestBody CreateBankAccountRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Bank account added",
                accountingService.createBankAccount(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/bank-accounts/{id}/transactions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List transactions for a bank account (paginated, newest first)")
    public ResponseEntity<ApiResponse<Page<BankTransactionResponse>>> getBankTransactions(
            @PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getBankTransactions(TenantContext.getTenantIdAsObject(), id, pageable)));
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

    // ── VAT ───────────────────────────────────────────────────────────────────

    @GetMapping("/vat-periods")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List VAT periods")
    public ResponseEntity<ApiResponse<List<VatPeriodResponse>>> getVatPeriods() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getVatPeriods(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/vat-periods")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Open a new VAT period (only one OPEN period allowed at a time)")
    public ResponseEntity<ApiResponse<VatPeriodResponse>> createVatPeriod(
            @Valid @RequestBody CreateVatPeriodRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("VAT period created",
                accountingService.createVatPeriod(TenantContext.getTenantIdAsObject(),
                        req.periodStart(), req.periodEnd())));
    }

    @PostMapping("/vat-periods/{id}/close")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Close an open VAT period")
    public ResponseEntity<ApiResponse<VatPeriodResponse>> closeVatPeriod(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("VAT period closed",
                accountingService.closeVatPeriod(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/reports/vat201")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "VAT201 summary — output VAT, input VAT, net payable for a date range")
    public ResponseEntity<ApiResponse<Vat201Response>> getVat201(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getVat201(TenantContext.getTenantIdAsObject(), from, to)));
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
    @Operation(summary = "Trial balance with gross debit and credit columns for a date range")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getTrialBalance(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/reports/balance-sheet")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Balance sheet as at a date (includes opening balances)")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getBalanceSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getBalanceSheet(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/reports/ar-aging")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Aged debtors report — outstanding invoices bucketed by days overdue")
    public ResponseEntity<ApiResponse<AgingReportResponse>> getArAging() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getArAging(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/reports/profit-and-loss/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download Profit & Loss as PDF")
    public ResponseEntity<byte[]> getProfitAndLossPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportPdfService.generateProfitAndLoss(
                TenantContext.getTenantIdAsObject(), from, to);
        return pdfResponse(pdf, "profit-and-loss-" + from + "-to-" + to + ".pdf");
    }

    @GetMapping("/reports/balance-sheet/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download Balance Sheet as PDF")
    public ResponseEntity<byte[]> getBalanceSheetPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportPdfService.generateBalanceSheet(
                TenantContext.getTenantIdAsObject(), from, to);
        return pdfResponse(pdf, "balance-sheet-" + from + "-to-" + to + ".pdf");
    }

    @GetMapping("/reports/trial-balance/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download Trial Balance as PDF")
    public ResponseEntity<byte[]> getTrialBalancePdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportPdfService.generateTrialBalance(
                TenantContext.getTenantIdAsObject(), from, to);
        return pdfResponse(pdf, "trial-balance-" + from + "-to-" + to + ".pdf");
    }

    @GetMapping("/reports/vat201/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download VAT201 summary as PDF")
    public ResponseEntity<byte[]> getVat201Pdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportPdfService.generateVat201(
                TenantContext.getTenantIdAsObject(), from, to);
        return pdfResponse(pdf, "vat201-" + from + "-to-" + to + ".pdf");
    }

    // ── Chart data endpoints ──────────────────────────────────────────────────

    @GetMapping("/reports/monthly-summary")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Monthly revenue vs expenses for the last N months — used for dashboard charts")
    public ResponseEntity<ApiResponse<List<MonthlySummaryResponse>>> getMonthlySummary(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accountingService.getMonthlySummary(TenantContext.getTenantIdAsObject(), months)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}
