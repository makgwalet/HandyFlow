package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.AccountantService;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accountant")
@RequiredArgsConstructor
@Tag(name = "Accountant", description = "Practice management, SARS compliance, time tracking and billing")
public class AccountantController {

    private final AccountantService accountantService;

    // ── Portfolio dashboard ───────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Portfolio dashboard — all KPIs, urgent deadlines, outstanding invoices")
    public ResponseEntity<ApiResponse<PortfolioDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard",
                accountantService.getPortfolioDashboard(TenantContext.getTenantIdAsObject())));
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List all clients in portfolio")
    public ResponseEntity<ApiResponse<Page<ClientResponse>>> getClients(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Clients",
                accountantService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<ClientResponse>> getClient(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Client",
                accountantService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Register a new client — entity classification, SARS numbers, year-end config")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(
            @Valid @RequestBody CreateClientRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client created",
                accountantService.createClient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<ClientResponse>> updateClient(
            @PathVariable UUID id, @RequestBody UpdateClientRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                accountantService.updateClient(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('USER_DELETE','ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        accountantService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client archived", null));
    }

    @PostMapping("/clients/{id}/fica-complete")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Mark FICA compliance as complete for a client")
    public ResponseEntity<ApiResponse<Void>> markFicaComplete(@PathVariable UUID id) {
        accountantService.markFicaComplete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("FICA marked complete", null));
    }

    @PostMapping("/clients/{id}/sars-agent")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Mark SARS agent appointment as completed (POA on file)")
    public ResponseEntity<ApiResponse<Void>> markSarsAgent(@PathVariable UUID id) {
        accountantService.markSarsAgentAppointed(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("SARS agent appointment recorded", null));
    }

    // ── SARS tax calendar ─────────────────────────────────────────────────────

    @GetMapping("/clients/{id}/deadlines")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List all SARS deadlines for a client")
    public ResponseEntity<ApiResponse<List<TaxDeadlineResponse>>> getClientDeadlines(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Deadlines",
                accountantService.getClientDeadlines(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/deadlines/generate")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Auto-generate SARS deadlines for a client for a given year — business-day adjusted")
    public ResponseEntity<ApiResponse<List<TaxDeadlineResponse>>> generateDeadlines(
            @PathVariable UUID id, @Valid @RequestBody GenerateDeadlinesRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Deadlines generated",
                accountantService.generateDeadlines(TenantContext.getTenantIdAsObject(), id, req.periodYear())));
    }

    @PostMapping("/clients/{clientId}/deadlines/{deadlineId}/file")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Mark a filing as submitted — store SARS reference number and amount")
    public ResponseEntity<ApiResponse<TaxDeadlineResponse>> fileDeadline(
            @PathVariable UUID clientId, @PathVariable UUID deadlineId,
            @Valid @RequestBody FileDeadlineRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Filing recorded",
                accountantService.fileFiling(TenantContext.getTenantIdAsObject(), clientId, deadlineId, req)));
    }

    @GetMapping("/deadlines")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Portfolio deadline view — all clients × all deadline types in date range")
    public ResponseEntity<ApiResponse<List<TaxDeadlineResponse>>> getPortfolioDeadlines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to   != null ? to   : LocalDate.now().plusDays(90);
        return ResponseEntity.ok(ApiResponse.success("Portfolio deadlines",
                accountantService.getPortfolioDeadlines(TenantContext.getTenantIdAsObject(), f, t)));
    }

    // ── Accounting core ───────────────────────────────────────────────────────

    @PostMapping("/clients/{id}/journals")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Create a journal entry — validates debits = credits before saving")
    public ResponseEntity<ApiResponse<JournalResponse>> createJournal(
            @PathVariable UUID id, @Valid @RequestBody CreateJournalRequest req) {
        UUID preparedBy = TenantContext.getCurrentUserId();  // from JWT
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Journal created",
                accountantService.createJournal(TenantContext.getTenantIdAsObject(), id, req, preparedBy)));
    }

    @PostMapping("/journals/{id}/review")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Submit journal for review (PREPARED → REVIEWED)")
    public ResponseEntity<ApiResponse<JournalResponse>> reviewJournal(
            @PathVariable UUID id, @RequestParam UUID clientId) {
        UUID reviewer = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Journal submitted for review",
                accountantService.approveJournal(TenantContext.getTenantIdAsObject(), clientId, id, reviewer)));
    }

    @PostMapping("/journals/{id}/post")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Approve and post journal (REVIEWED → APPROVED → POSTED)")
    public ResponseEntity<ApiResponse<JournalResponse>> postJournal(
            @PathVariable UUID id, @RequestParam UUID clientId) {
        UUID approver = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Journal posted",
                accountantService.postJournal(TenantContext.getTenantIdAsObject(), clientId, id, approver)));
    }

    // ── Time tracking ─────────────────────────────────────────────────────────

    @PostMapping("/time")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Log a time entry — billable or non-billable, linked to a client")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> logTime(
            @Valid @RequestBody CreateTimeEntryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Time logged",
                accountantService.logTime(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/clients/{id}/time/unbilled")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List unbilled (WIP) time entries for a client")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getUnbilledTime(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Unbilled time",
                accountantService.getUnbilledTime(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Billing ───────────────────────────────────────────────────────────────

    @PostMapping("/fee-notes")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Generate a fee note from unbilled time entries or a fixed fee")
    public ResponseEntity<ApiResponse<FeeNoteResponse>> generateFeeNote(
            @Valid @RequestBody CreateFeeNoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fee note generated",
                accountantService.generateFeeNote(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/fee-notes/{id}/send")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Send fee note to client via email")
    public ResponseEntity<ApiResponse<FeeNoteResponse>> sendFeeNote(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Fee note sent",
                accountantService.sendFeeNote(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/fee-notes/outstanding")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Debtors aging — all outstanding invoices across all clients")
    public ResponseEntity<ApiResponse<List<FeeNoteResponse>>> getOutstanding() {
        return ResponseEntity.ok(ApiResponse.success("Outstanding invoices",
                accountantService.getOutstandingInvoices(TenantContext.getTenantIdAsObject())));
    }
}
