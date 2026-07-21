package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    // NEW: closes the audit's "client-facing deadline reminder emails"
    // gap — the toggle behind the per-client opt-out.
    @PostMapping("/clients/{id}/deadline-reminders")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Enable or disable client-facing deadline reminder emails for a client")
    public ResponseEntity<ApiResponse<ClientResponse>> setClientDeadlineReminders(
            @PathVariable UUID id, @RequestParam boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success("Preference updated",
                accountantService.setClientDeadlineRemindersEnabled(TenantContext.getTenantIdAsObject(), id, enabled)));
    }

    // ── FICA / KYC documents ────────────────────────────────────────────────
    // NEW: closes the audit's "document/attachment storage on client
    // records" gap. Maps to acc_fica_documents, a table that already
    // existed with no application-layer code at all.

    @GetMapping("/clients/{id}/fica-documents")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List FICA/KYC documents for a client — metadata only, no file content")
    public ResponseEntity<ApiResponse<List<FicaDocumentResponse>>> getFicaDocuments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("FICA documents",
                accountantService.getFicaDocuments(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/fica-documents")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Upload a FICA/KYC document for a client (max 10MB)")
    public ResponseEntity<ApiResponse<FicaDocumentResponse>> uploadFicaDocument(
            @PathVariable UUID id, @Valid @RequestBody UploadFicaDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                accountantService.uploadFicaDocument(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping(value = "/clients/{clientId}/fica-documents/{docId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Download a FICA/KYC document")
    public ResponseEntity<byte[]> downloadFicaDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        var file = accountantService.downloadFicaDocument(TenantContext.getTenantIdAsObject(), clientId, docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/clients/{clientId}/fica-documents/{docId}/verify")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Mark a FICA/KYC document as verified")
    public ResponseEntity<ApiResponse<FicaDocumentResponse>> verifyFicaDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.success("Document verified",
                accountantService.verifyFicaDocument(TenantContext.getTenantIdAsObject(), clientId, docId,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/clients/{clientId}/fica-documents/{docId}")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Delete a FICA/KYC document")
    public ResponseEntity<ApiResponse<Void>> deleteFicaDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        accountantService.deleteFicaDocument(TenantContext.getTenantIdAsObject(), clientId, docId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
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

    // NEW: closes the accountant module audit's "bulk deadline
    // generation" quick-win gap. Portfolio-level (no {id}), matching
    // GET /deadlines' own convention — this is explicitly for every
    // client at once, not nested under a single client's path.
    @PostMapping("/deadlines/generate-all")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Auto-generate SARS deadlines for every active client for a given year — business-day adjusted")
    public ResponseEntity<ApiResponse<BulkDeadlineGenerationResponse>> generateDeadlinesForAllClients(
            @Valid @RequestBody GenerateDeadlinesRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Bulk deadline generation complete",
                accountantService.generateDeadlinesForAllClients(TenantContext.getTenantIdAsObject(), req.periodYear())));
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

    // NEW: closes the #2 must-fix gap from the accountant module audit —
    // "journals are write-only... a double-entry accounting core that's
    // invisible to the user once posted." AccJournalRepository.
    // findByClient() already existed and was never called by anything
    // before this.
    @GetMapping("/clients/{id}/journals")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List journal entries for a client")
    public ResponseEntity<ApiResponse<Page<JournalResponse>>> getClientJournals(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Journals",
                accountantService.getClientJournals(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    // NEW: closes the "trial balance" gap.
    @GetMapping("/clients/{id}/trial-balance")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Trial balance for a client for a given period")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> getTrialBalance(
            @PathVariable UUID id,
            @RequestParam int periodYear, @RequestParam int periodMonth) {
        return ResponseEntity.ok(ApiResponse.success("Trial balance",
                accountantService.getTrialBalance(TenantContext.getTenantIdAsObject(), id, periodYear, periodMonth)));
    }

    // ── Chart of Accounts ────────────────────────────────────────────────────
    // NEW: closes the "minimal COA-seeding capability" gap.

    @GetMapping("/clients/{id}/coa-accounts")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List chart of accounts entries for a client")
    public ResponseEntity<ApiResponse<List<CoaAccountResponse>>> getCoaAccounts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Chart of accounts",
                accountantService.getCoaAccounts(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/coa-accounts")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Create a chart of accounts entry for a client")
    public ResponseEntity<ApiResponse<CoaAccountResponse>> createCoaAccount(
            @PathVariable UUID id, @Valid @RequestBody CreateCoaAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                accountantService.createCoaAccount(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/clients/{id}/coa-accounts/seed-standard")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Seed a standard starter chart of accounts — only for clients with none yet")
    public ResponseEntity<ApiResponse<List<CoaAccountResponse>>> seedStandardChartOfAccounts(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Standard chart of accounts seeded",
                accountantService.seedStandardChartOfAccounts(TenantContext.getTenantIdAsObject(), id)));
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
                accountantService.logTime(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    // NEW: closes the accountant module audit's "staff-level time
    // report" gap.
    @GetMapping("/time/staff-summary")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Time summary per staff member for a date range — hours, billable hours, amount billed")
    public ResponseEntity<ApiResponse<List<StaffTimeSummaryResponse>>> getStaffTimeSummary(
            @RequestParam java.time.LocalDate from, @RequestParam java.time.LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Staff time summary",
                accountantService.getStaffTimeSummary(TenantContext.getTenantIdAsObject(), from, to)));
    }

    // NEW: closes the audit's "time entry edit/delete" gap. Both reject
    // BILLED entries — see TimeEntry.isEditable()'s own comment.
    @PutMapping("/time/{id}")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Edit a time entry — only entries that haven't been billed can be edited")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> updateTimeEntry(
            @PathVariable UUID id, @Valid @RequestBody UpdateTimeEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Time entry updated",
                accountantService.updateTimeEntry(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/time/{id}")
    @PreAuthorize("hasAnyAuthority('USER_DELETE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Delete a time entry — only entries that haven't been billed can be deleted")
    public ResponseEntity<ApiResponse<Void>> deleteTimeEntry(@PathVariable UUID id) {
        accountantService.deleteTimeEntry(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Time entry deleted", null));
    }

    @GetMapping("/clients/{id}/time/unbilled")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List unbilled (WIP) time entries for a client")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getUnbilledTime(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Unbilled time",
                accountantService.getUnbilledTime(TenantContext.getTenantIdAsObject(), id)));
    }

    // NEW: closes the "unified client detail page" gap.
    @GetMapping("/clients/{id}/time")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Full time entry history for a client — every status, not just unbilled")
    public ResponseEntity<ApiResponse<Page<TimeEntryResponse>>> getClientTimeEntries(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Time entries",
                accountantService.getClientTimeEntries(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/clients/{id}/fee-notes")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Full fee note history for a client — every status")
    public ResponseEntity<ApiResponse<Page<FeeNoteResponse>>> getClientFeeNotes(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Fee notes",
                accountantService.getClientFeeNotes(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/clients/{id}/detail")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Unified client detail — recent deadlines, fee notes, journals, and time in one call")
    public ResponseEntity<ApiResponse<ClientDetailResponse>> getClientDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Client detail",
                accountantService.getClientDetail(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Client portal — staff-side invite management ────────────────────────
    // NEW: closes the "client portal" gap (staff-side layer only —
    // login/session mechanics are a separate, later layer).
    //
    // FIX: these three endpoints were only discoverable under the class-
    // level "Accountant" Swagger tag, mixed in with journals/deadlines/
    // fee-notes/everything else — confirmed via real testing that this
    // made them genuinely hard to find, since every OTHER portal-related
    // endpoint (auth, data access) lives under a separate "Accountant
    // Client Portal" tag. Method-level @Tag overrides fix the grouping
    // without moving the actual code or changing the URL path — this
    // stays a normal AccountantController endpoint, it's just now
    // discoverable where someone looking for it would actually look.

    @PostMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Tag(name = "Accountant Client Portal")
    @Operation(summary = "Invite a client contact to the client portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invitePortalUser(
            @PathVariable UUID id, @Valid @RequestBody InvitePortalUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                accountantService.invitePortalUser(TenantContext.getTenantIdAsObject(), id,
                        req.email(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Tag(name = "Accountant Client Portal")
    @Operation(summary = "List portal access grants (pending, active, revoked) for a client")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> getPortalAccessGrants(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Portal access grants",
                accountantService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/portal-invites/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Tag(name = "Accountant Client Portal")
    @Operation(summary = "Revoke a client's portal access")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revokePortalAccess(
            @PathVariable UUID clientId, @PathVariable UUID grantId) {
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                accountantService.revokePortalAccess(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
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

    // NEW: closes the #1 must-fix gap from the accountant module audit —
    // "billing has no money-in loop". Without this, a sent fee note had
    // no path to ever being marked paid.
    @PostMapping("/fee-notes/{id}/payments")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Record a payment against a fee note — updates status to PARTIAL or PAID")
    public ResponseEntity<ApiResponse<FeeNoteResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded",
                accountantService.recordPayment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/fee-notes/{id}/payments")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Payment history for a fee note")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPayments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Payment history",
                accountantService.getPayments(TenantContext.getTenantIdAsObject(), id)));
    }

    // NEW: closes the "quick win" gap from the accountant module audit —
    // "data model already has everything needed" for a fee note PDF.
    @GetMapping(value = "/fee-notes/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Download a Fee Note PDF")
    public ResponseEntity<byte[]> downloadFeeNotePdf(@PathVariable UUID id) {
        byte[] pdf = accountantService.generateFeeNotePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/fee-notes/outstanding")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Debtors aging — all outstanding invoices across all clients")
    public ResponseEntity<ApiResponse<List<FeeNoteResponse>>> getOutstanding() {
        return ResponseEntity.ok(ApiResponse.success("Outstanding invoices",
                accountantService.getOutstandingInvoices(TenantContext.getTenantIdAsObject())));
    }

    // ── Practice profile ──────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Get the accountant firm's practice profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success("Profile",
                accountantService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/profile")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Create or update the accountant firm's practice profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> upsertProfile(
            @Valid @RequestBody CreateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                accountantService.upsertProfile(TenantContext.getTenantIdAsObject(), req)));
    }
}