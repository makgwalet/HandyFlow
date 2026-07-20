package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.*;
import za.co.handyflow.platform.accountant.domain.repository.*;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountantService {

    private final AccClientRepository      clientRepo;
    private final TaxDeadlineRepository    deadlineRepo;
    private final AccJournalRepository     journalRepo;
    private final TimeEntryRepository      timeEntryRepo;
    private final FeeNoteRepository        feeNoteRepo;
    private final DeadlineEngine           deadlineEngine;
    private final FeeNoteNumberGenerator   feeNoteNumberGen;
    private final AccountantProfileRepository profileRepo;
    private final EmailService             emailService;
    // NEW: backs recordPayment()/getPayments() — closes the #1 must-fix
    // gap from the audit ("billing has no money-in loop").
    private final AccPaymentReceivedRepository paymentRepo;
    // NEW: backs generateFeeNotePdf() — closes the "quick win" gap from
    // the audit ("data model already has everything needed").
    private final AccFeeNotePdfGenerator   feeNotePdfGenerator;
    // NEW: backs the FICA document upload/list/download/verify/delete
    // methods below — closes the audit's "document/attachment storage
    // on client records" gap.
    private final AccFicaDocumentRepository ficaDocRepo;
    // NEW: back getTrialBalance() and the account-name fix in
    // toJournalResponse() — closes both gaps unlocked by finding
    // acc_periods/acc_coa_accounts already existed with no
    // application-layer code.
    private final AccPeriodRepository       periodRepo;
    private final AccCoaAccountRepository   coaAccountRepo;
    private final AccJournalLineRepository  journalLineRepo;
    // NEW: backs invitePortalUser()/getPortalAccessGrants()/
    // revokePortalAccess() — closes the "client portal" gap (staff-side
    // invite management layer).
    private final AccPortalAccessGrantRepository portalGrantRepo;

    // ── L2: Client portfolio ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        return clientRepo.findAllActive(tenantId, pageable)
                .map(c -> toClientResponse(c, tenantId));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .map(c -> toClientResponse(c, tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));
    }

    @Transactional
    public ClientResponse createClient(TenantId tenantId, CreateClientRequest req) {
        AccClient client = AccClient.create(tenantId, req.entityType(), req.tradingName(),
                req.registeredName(), req.registrationNumber(), req.taxReferenceNumber(),
                req.vatNumber(), req.vatCategory(), req.yearEndMonth(),
                req.contactEmail(), req.contactPhone());
        clientRepo.save(client);
        log.info("Created acc_client={} entity={} tenant={}", client.getTradingName(), client.getEntityType(), tenantId);
        return toClientResponse(client, tenantId);
    }

    @Transactional
    public ClientResponse updateClient(TenantId tenantId, UUID clientId, UpdateClientRequest req) {
        AccClient client = findActive(tenantId, clientId);
        // apply non-null fields via domain methods
        if (req.riskRating() != null) client.updateRisk(req.riskRating());
        clientRepo.save(client);
        return toClientResponse(client, tenantId);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID clientId) {
        AccClient client = findActive(tenantId, clientId);
        // Guard: cannot delete client with outstanding invoices
        BigDecimal outstanding = feeNoteRepo.findOutstanding(tenantId.getValue()).stream()
                .filter(f -> f.getClientId().equals(clientId))
                .map(FeeNote::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.compareTo(BigDecimal.ZERO) > 0)
            throw new HandyFlowException(
                    "Cannot archive client with outstanding invoices. Settle all fees first.",
                    HttpStatus.CONFLICT, "CLIENT_HAS_OUTSTANDING_INVOICES");
        client.softDelete();
        clientRepo.save(client);
    }

    @Transactional
    public void markFicaComplete(TenantId tenantId, UUID clientId) {
        AccClient client = findActive(tenantId, clientId);
        client.markFicaComplete();
        clientRepo.save(client);
    }

    @Transactional
    public void markSarsAgentAppointed(TenantId tenantId, UUID clientId) {
        AccClient client = findActive(tenantId, clientId);
        client.markSarsAgentAppointed();
        clientRepo.save(client);
    }

    // ── L4: SARS tax calendar ─────────────────────────────────────────────────

    /**
     * Auto-generates all SARS deadlines for a client for a given year.
     * Uses DeadlineEngine which applies business-day adjustment logic
     * (EMP201 due 7th; VAT201 due 25th; ITR14 due 12m after year-end etc.)
     */
    @Transactional
    public List<TaxDeadlineResponse> generateDeadlines(TenantId tenantId, UUID clientId, int year) {
        AccClient client = findActive(tenantId, clientId);
        List<TaxDeadline> generated = deadlineEngine.generateForClient(client, year);
        generated.forEach(d -> {
            // Skip if already exists (idempotent re-generation)
            boolean exists = deadlineRepo.findByClient(clientId).stream()
                    .anyMatch(existing -> existing.getDeadlineType().equals(d.getDeadlineType())
                            && existing.getPeriodYear() == d.getPeriodYear()
                            && java.util.Objects.equals(existing.getPeriodMonth(), d.getPeriodMonth()));
            if (!exists) deadlineRepo.save(d);
        });
        return deadlineRepo.findByClient(clientId).stream()
                .filter(d -> d.getPeriodYear() == year)
                .map(this::toDeadlineResponse)
                .sorted(java.util.Comparator.comparing(TaxDeadlineResponse::adjustedDueDate))
                .toList();
    }

    /**
     * NEW: closes the accountant module audit's "bulk deadline
     * generation" quick-win gap — generateDeadlines() was per-client
     * only; a practice with 100 clients had to click through each one
     * individually at year-start.
     * <p>
     * Reuses generateDeadlines()'s own per-client logic exactly,
     * including its existing idempotency check (safe to re-run without
     * creating duplicates or hitting the unique constraint on
     * acc_tax_deadlines), just looped across every active client for
     * the tenant. Per-client failures are caught and isolated — one
     * client's DeadlineEngine issue must never abort the whole batch
     * for every other client, so this returns a summary (succeeded
     * count + which clients failed and why) rather than either
     * silently swallowing failures or letting one bad client kill the
     * entire run.
     */
    @Transactional
    public BulkDeadlineGenerationResponse generateDeadlinesForAllClients(TenantId tenantId, int year) {
        List<AccClient> clients = clientRepo.findAllActive(tenantId, Pageable.unpaged()).getContent();
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (AccClient client : clients) {
            try {
                generateDeadlines(tenantId, client.getId(), year);
                succeeded++;
            } catch (Exception e) {
                log.error("Bulk deadline generation failed for client={} ({}): {}",
                        client.getId(), client.getTradingName(), e.getMessage());
                failures.add(client.getTradingName() + ": " + e.getMessage());
            }
        }
        log.info("Bulk deadline generation for tenant={} year={}: {} succeeded, {} failed",
                tenantId.getValue(), year, succeeded, failures.size());
        return new BulkDeadlineGenerationResponse(clients.size(), succeeded, failures);
    }

    @Transactional(readOnly = true)
    public List<TaxDeadlineResponse> getClientDeadlines(TenantId tenantId, UUID clientId) {
        findActive(tenantId, clientId);
        return deadlineRepo.findByClient(clientId).stream()
                .map(this::toDeadlineResponse)
                .sorted(java.util.Comparator.comparing(TaxDeadlineResponse::adjustedDueDate))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxDeadlineResponse> getPortfolioDeadlines(TenantId tenantId, LocalDate from, LocalDate to) {
        return deadlineRepo.findInDateRange(tenantId.getValue(), from, to).stream()
                .map(this::toDeadlineResponse)
                .toList();
    }

    @Transactional
    public TaxDeadlineResponse fileFiling(TenantId tenantId, UUID clientId, UUID deadlineId,
                                          FileDeadlineRequest req) {
        TaxDeadline deadline = deadlineRepo.findById(deadlineId)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline", deadlineId.toString()));
        deadline.markFiled(req.filedDate(), req.sarsReference(), req.filingAmount());
        deadlineRepo.save(deadline);
        log.info("Filed {} for client={} ref={}", deadline.getDeadlineType(), clientId, req.sarsReference());
        return toDeadlineResponse(deadline);
    }

    // ── L3: Accounting core ───────────────────────────────────────────────────

    @Transactional
    public JournalResponse createJournal(TenantId tenantId, UUID clientId,
                                         CreateJournalRequest req, UUID preparedBy) {
        findActive(tenantId, clientId);

        // Validate balanced before saving
        BigDecimal debits  = req.lines().stream()
                .map(l -> l.debit()  != null ? l.debit()  : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = req.lines().stream()
                .map(l -> l.credit() != null ? l.credit() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0)
            throw new HandyFlowException(
                    String.format("Journal is not balanced: debits R%s ≠ credits R%s", debits, credits),
                    HttpStatus.BAD_REQUEST, "JOURNAL_UNBALANCED");

        // FIX: was "// TODO: validate period not locked (check
        // acc_periods.status)" plus req.periodId() passed straight
        // through with no resolution at all — there was no way for any
        // caller to obtain a valid periodId, since no period-creation
        // or period-listing endpoint existed anywhere. Periods now
        // resolve-or-create from year/month, and the pre-existing TODO
        // is genuinely checkable now that a real AccPeriod is in hand,
        // not just a raw UUID passed through blind.
        AccPeriod period = periodRepo.findByClientAndYearMonth(clientId, req.periodYear(), req.periodMonth())
                .orElseGet(() -> periodRepo.save(
                        AccPeriod.create(tenantId.getValue(), clientId, req.periodYear(), req.periodMonth())));
        if ("LOCKED".equals(period.getStatus())) {
            throw new HandyFlowException(
                    String.format("Period %d/%d is locked and cannot accept new journals",
                            req.periodMonth(), req.periodYear()),
                    HttpStatus.BAD_REQUEST, "PERIOD_LOCKED");
        }

        AccJournal journal = AccJournal.create(tenantId.getValue(), clientId,
                period.getId(), req.reference(), req.description(),
                req.journalType(), req.journalDate(), preparedBy);

        int order = 0;
        for (var line : req.lines()) {
            AccJournalLine jl = (line.debit() != null && line.debit().compareTo(BigDecimal.ZERO) > 0)
                    ? AccJournalLine.debit(tenantId.getValue(), journal.getId(), line.accountId(), line.debit(), line.description(), order++)
                    : AccJournalLine.credit(tenantId.getValue(), journal.getId(), line.accountId(), line.credit(), line.description(), order++);
            journal.getLines().add(jl);
        }

        journalRepo.save(journal);
        log.info("Created journal={} client={} type={}", journal.getReference(), clientId, journal.getJournalType());
        return toJournalResponse(journal);
    }

    @Transactional
    public JournalResponse approveJournal(TenantId tenantId, UUID clientId, UUID journalId, UUID reviewer) {
        // FIX: was journalRepo.findById(journalId) — no tenant check at
        // all. Same gap already fixed on FeeNoteRepository/sendFeeNote();
        // see AccJournalRepository.findByTenantIdAndId()'s own comment.
        AccJournal journal = journalRepo.findByTenantIdAndId(tenantId.getValue(), journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal", journalId.toString()));
        // NEW: clientId was already a parameter here but never actually
        // checked against anything — verifies this journal genuinely
        // belongs to the client the caller thinks it does, not just any
        // journal in the same tenant.
        if (!journal.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Journal", journalId.toString());
        }
        journal.submitForReview(reviewer);
        journalRepo.save(journal);
        return toJournalResponse(journal);
    }

    @Transactional
    public JournalResponse postJournal(TenantId tenantId, UUID clientId, UUID journalId, UUID approver) {
        AccJournal journal = journalRepo.findByTenantIdAndId(tenantId.getValue(), journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal", journalId.toString()));
        if (!journal.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Journal", journalId.toString());
        }
        journal.approve(approver);
        journal.post();
        journalRepo.save(journal);
        log.info("Posted journal={} client={}", journal.getReference(), clientId);
        return toJournalResponse(journal);
    }

    /**
     * NEW: closes the #2 must-fix gap from the accountant module audit —
     * "journals are write-only... invisible to the user once posted".
     * AccJournalRepository.findByClient() already existed and was never
     * called by anything before this. Tenant isolation is enforced by
     * verifying the client itself belongs to the caller's tenant first
     * (findActive() below) — journals are only ever reached through a
     * specific client, so this is sufficient without a separate
     * tenant-scoped journal query for the list case.
     */
    @Transactional(readOnly = true)
    public Page<JournalResponse> getClientJournals(TenantId tenantId, UUID clientId, Pageable pageable) {
        clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));
        return journalRepo.findByClient(clientId, pageable).map(this::toJournalResponse);
    }

    /**
     * NEW: closes the "trial balance" gap — unlocked by finding
     * acc_periods and acc_coa_accounts already existed with no
     * application-layer code. See AccJournalLineRepository's own class
     * Javadoc for why the aggregation queries it uses are native SQL,
     * not JPQL.
     * <p>
     * Includes every ACTIVE account for the client regardless of
     * whether it has any balance — a trial balance's job is to be a
     * complete picture (a R0.00 line still confirms an account was
     * reconciled to zero, not silently omitted), not just the accounts
     * that happen to have activity.
     * <p>
     * totalDebits/totalCredits are the sum of positive and negative
     * CLOSING balances respectively (the textbook meaning of "trial
     * balance" — proving the books balance), not period movement.
     * Per-account period movement is still visible in each line's own
     * periodDebits/periodCredits.
     */
    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(TenantId tenantId, UUID clientId, int periodYear, int periodMonth) {
        clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));

        List<AccCoaAccount> accounts = coaAccountRepo.findActiveByClient(clientId);

        Map<UUID, AccJournalLineRepository.AccountBalanceRow> before =
                journalLineRepo.sumByAccountBeforePeriod(clientId, periodYear, periodMonth).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AccJournalLineRepository.AccountBalanceRow::getAccountId, r -> r));

        // Only queried if a period record actually exists for this
        // year/month — if none does, there can be no journals pointing
        // at it, so period movement is simply zero for every account.
        Map<UUID, AccJournalLineRepository.AccountBalanceRow> current = periodRepo
                .findByClientAndYearMonth(clientId, periodYear, periodMonth)
                .map(p -> journalLineRepo.sumByAccountForPeriod(clientId, p.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AccJournalLineRepository.AccountBalanceRow::getAccountId, r -> r)))
                .orElse(Map.of());

        List<TrialBalanceLine> lines = new ArrayList<>();
        for (AccCoaAccount acc : accounts) {
            AccJournalLineRepository.AccountBalanceRow b = before.get(acc.getId());
            AccJournalLineRepository.AccountBalanceRow c = current.get(acc.getId());
            BigDecimal opening = b != null ? b.getTotalDebit().subtract(b.getTotalCredit()) : BigDecimal.ZERO;
            BigDecimal periodDebits  = c != null ? c.getTotalDebit()  : BigDecimal.ZERO;
            BigDecimal periodCredits = c != null ? c.getTotalCredit() : BigDecimal.ZERO;
            BigDecimal closing = opening.add(periodDebits).subtract(periodCredits);
            lines.add(new TrialBalanceLine(acc.getAccountCode(), acc.getAccountName(), acc.getAccountType(),
                    opening, periodDebits, periodCredits, closing));
        }

        BigDecimal totalDebits = lines.stream().map(TrialBalanceLine::closingBalance)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = lines.stream().map(TrialBalanceLine::closingBalance)
                .filter(v -> v.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::negate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanced = totalDebits.compareTo(totalCredits) == 0;

        return new TrialBalanceResponse(clientId, periodYear, periodMonth, lines, totalDebits, totalCredits, balanced);
    }

    // ── Chart of Accounts ────────────────────────────────────────────────────
    // NEW: closes the "minimal COA-seeding capability" gap — trial
    // balance needed something real to compute against, and there was
    // no way to create chart-of-accounts entries at all until now.
    //
    // A small, SA-oriented starter chart spanning all five required
    // account types (ASSET/LIABILITY/EQUITY/INCOME/EXPENSE), enough to
    // post real journal entries against — not a complete, firm-specific
    // chart. A real practice would still customize this per client.
    private record StandardAccount(String code, String name, String type,
                                   String subType, boolean vatApplicable, String vatType) {}

    private static final List<StandardAccount> STANDARD_COA = List.of(
            new StandardAccount("1000", "Bank",                 "ASSET",     "Current Asset",      false, null),
            new StandardAccount("1100", "Accounts Receivable",  "ASSET",     "Current Asset",       false, null),
            new StandardAccount("2000", "Accounts Payable",     "LIABILITY", "Current Liability",   false, null),
            new StandardAccount("2100", "VAT Control",          "LIABILITY", "Current Liability",   true,  "OUTPUT"),
            new StandardAccount("3000", "Share Capital",        "EQUITY",    null,                  false, null),
            new StandardAccount("3100", "Retained Earnings",    "EQUITY",    null,                  false, null),
            new StandardAccount("4000", "Sales",                "INCOME",    "Revenue",             true,  "OUTPUT"),
            new StandardAccount("5000", "Cost of Sales",        "EXPENSE",   "Cost of Sales",        true,  "INPUT"),
            new StandardAccount("5100", "Salaries & Wages",     "EXPENSE",   "Operating Expense",   false, null),
            new StandardAccount("5200", "Rent Expense",         "EXPENSE",   "Operating Expense",   true,  "INPUT"),
            new StandardAccount("5300", "Bank Charges",         "EXPENSE",   "Operating Expense",   false, null),
            new StandardAccount("5400", "Professional Fees",    "EXPENSE",   "Operating Expense",   true,  "INPUT")
    );

    @Transactional(readOnly = true)
    public List<CoaAccountResponse> getCoaAccounts(TenantId tenantId, UUID clientId) {
        findActive(tenantId, clientId);
        return coaAccountRepo.findActiveByClient(clientId).stream()
                .map(this::toCoaAccountResponse).toList();
    }

    @Transactional
    public CoaAccountResponse createCoaAccount(TenantId tenantId, UUID clientId, CreateCoaAccountRequest req) {
        findActive(tenantId, clientId);
        AccCoaAccount acc = AccCoaAccount.create(tenantId.getValue(), clientId, req.accountCode(), req.accountName(),
                req.accountType(), req.subType(), req.vatApplicable(), req.vatType());
        coaAccountRepo.save(acc);
        return toCoaAccountResponse(acc);
    }

    /**
     * Only seeds a client that has no chart of accounts entries at
     * all — deliberately refuses on a non-empty chart rather than
     * silently duplicating or skipping, since either behavior could
     * hide a real mistake (re-seeding a client that already has a real,
     * customized chart).
     */
    @Transactional
    public List<CoaAccountResponse> seedStandardChartOfAccounts(TenantId tenantId, UUID clientId) {
        findActive(tenantId, clientId);
        List<AccCoaAccount> existing = coaAccountRepo.findActiveByClient(clientId);
        if (!existing.isEmpty()) {
            throw new IllegalStateException(
                    "This client already has " + existing.size() + " chart of accounts entries — "
                            + "seeding is only for clients starting from empty, to avoid creating duplicates");
        }
        List<AccCoaAccount> seeded = STANDARD_COA.stream()
                .map(s -> AccCoaAccount.create(tenantId.getValue(), clientId, s.code(), s.name(),
                        s.type(), s.subType(), s.vatApplicable(), s.vatType()))
                .toList();
        coaAccountRepo.saveAll(seeded);
        log.info("Seeded {} standard chart of accounts entries for client={}", seeded.size(), clientId);
        return seeded.stream().map(this::toCoaAccountResponse).toList();
    }

    private CoaAccountResponse toCoaAccountResponse(AccCoaAccount a) {
        return new CoaAccountResponse(a.getId(), a.getAccountCode(), a.getAccountName(), a.getAccountType(),
                a.getSubType(), a.isVatApplicable(), a.getVatType(), a.isActive());
    }

    // ── Client detail ────────────────────────────────────────────────────────
    // NEW: closes the "unified client detail page" gap.

    /**
     * Full fee note history for a client — every status, not just
     * outstanding. GET /fee-notes/outstanding is portfolio-wide and
     * excludes DRAFT/PAID/WRITTEN_OFF; this is the client-scoped
     * equivalent with nothing filtered out. FeeNoteRepository.
     * findByClient() already existed, unused, before this.
     */
    @Transactional(readOnly = true)
    public Page<FeeNoteResponse> getClientFeeNotes(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActive(tenantId, clientId);
        return feeNoteRepo.findByClient(clientId, pageable).map(this::toFeeNoteResponse);
    }

    /**
     * Full time entry history for a client — every status (UNBILLED,
     * BILLED, NON_BILLABLE, WRITTEN_OFF), not just unbilled WIP.
     * GET /clients/{id}/time/unbilled only ever returns UNBILLED
     * entries; this is the complete history. TimeEntryRepository.
     * findByClient() already existed, unused, before this.
     */
    @Transactional(readOnly = true)
    public Page<TimeEntryResponse> getClientTimeEntries(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActive(tenantId, clientId);
        return timeEntryRepo.findByClient(clientId, pageable).map(this::toTimeEntryResponse);
    }

    /**
     * The actual "unified client detail" aggregate — see
     * ClientDetailResponse's own class Javadoc for why each list is
     * capped to 10 rather than fully paginated here.
     */
    @Transactional(readOnly = true)
    public ClientDetailResponse getClientDetail(TenantId tenantId, UUID clientId) {
        AccClient client = findActive(tenantId, clientId);
        ClientResponse clientResponse = toClientResponse(client, tenantId);

        List<TaxDeadlineResponse> deadlines = getClientDeadlines(tenantId, clientId).stream()
                .limit(10).toList();
        List<FeeNoteResponse> feeNotes = feeNoteRepo.findByClient(clientId, PageRequest.of(0, 10)).stream()
                .map(this::toFeeNoteResponse).toList();
        List<JournalResponse> journals = journalRepo.findByClient(clientId, PageRequest.of(0, 10)).stream()
                .map(this::toJournalResponse).toList();
        List<TimeEntryResponse> timeEntries = timeEntryRepo.findByClient(clientId, PageRequest.of(0, 10)).stream()
                .map(this::toTimeEntryResponse).toList();

        return new ClientDetailResponse(clientResponse, deadlines, feeNotes, journals, timeEntries);
    }

    // ── Client portal — staff-side invite management ────────────────────────
    // NEW: closes the "client portal" gap. Login/session mechanics
    // (PortalAuthService, PortalJwtFilter) are a separate, later layer —
    // this is only the staff-authenticated side: invite a client contact,
    // see who's been invited, revoke access. None of this touches JWTs
    // at all; it's the same @PreAuthorize/TenantContext pattern already
    // used everywhere else in this controller.

    @Transactional
    public PortalAccessGrantResponse invitePortalUser(TenantId tenantId, UUID clientId,
                                                      String email, UUID invitedBy) {
        AccClient client = findActive(tenantId, clientId);

        // Refuses a duplicate invite for the same email+client rather
        // than silently creating a second grant or silently doing
        // nothing — either would be confusing for staff to reason
        // about later.
        boolean alreadyGranted = portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new IllegalStateException(
                    "This email already has an active or pending portal invite for this client");
        }

        AccPortalAccessGrant grant = AccPortalAccessGrant.createInvite(tenantId.getValue(), clientId, email, invitedBy);
        portalGrantRepo.save(grant);

        AccountantProfile profile = profileRepo.findByTenantId(tenantId).orElse(null);
        String firmName = profile != null ? profile.getFirmName() : "your accountant";
        // Placeholder path — see EmailTemplates.portalInvite()'s own
        // comment for why this is a real link to a page that doesn't
        // exist yet, not omitted.
        String acceptUrl = "https://handyflow.co.za/accountant/portal/auth/accept-invite?token=" + grant.getInviteToken();

        emailService.send(email,
                "You've been invited to the " + firmName + " client portal",
                za.co.handyflow.platform.shared.EmailTemplates.portalInvite(
                        client.getTradingName(), firmName, acceptUrl));

        log.info("Portal invite sent to {} for client={}", email, clientId);
        return toPortalAccessGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID clientId) {
        findActive(tenantId, clientId);
        return portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .map(this::toPortalAccessGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revokePortalAccess(TenantId tenantId, UUID clientId,
                                                        UUID grantId, UUID revokedBy) {
        findActive(tenantId, clientId);
        AccPortalAccessGrant grant = portalGrantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        portalGrantRepo.save(grant);
        log.info("Portal access revoked for grant={} client={} by={}", grantId, clientId, revokedBy);
        return toPortalAccessGrantResponse(grant);
    }

    private PortalAccessGrantResponse toPortalAccessGrantResponse(AccPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }

    // ── L6: Time tracking & billing ───────────────────────────────────────────

    @Transactional
    public TimeEntryResponse logTime(TenantId tenantId, CreateTimeEntryRequest req) {
        findActive(tenantId, req.clientId());
        TimeEntry entry = TimeEntry.create(tenantId.getValue(), req.clientId(), req.practitionerId(),
                req.entryDate(), req.activityType(), req.description(),
                req.hours(), req.hourlyRate(), req.billable());
        timeEntryRepo.save(entry);
        return toTimeEntryResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> getUnbilledTime(TenantId tenantId, UUID clientId) {
        return timeEntryRepo.findUnbilledByClient(clientId).stream()
                .map(this::toTimeEntryResponse).toList();
    }

    /**
     * NEW: closes the accountant module audit's "time entry edit/delete"
     * gap — a wrong hour/rate entry previously couldn't be corrected
     * once logged. See TimeEntry.isEditable()'s own comment for why
     * BILLED entries are excluded — the actual business rule lives on
     * the entity, not here.
     */
    @Transactional
    public TimeEntryResponse updateTimeEntry(TenantId tenantId, UUID entryId, UpdateTimeEntryRequest req) {
        TimeEntry entry = findOwnTimeEntry(tenantId, entryId);
        entry.update(req.entryDate(), req.activityType(), req.description(),
                req.hours(), req.hourlyRate(), req.billable());
        timeEntryRepo.save(entry);
        return toTimeEntryResponse(entry);
    }

    @Transactional
    public void deleteTimeEntry(TenantId tenantId, UUID entryId) {
        TimeEntry entry = findOwnTimeEntry(tenantId, entryId);
        if (!entry.isEditable()) {
            throw new IllegalStateException(
                    "Cannot delete a time entry that has already been billed"
                            + (entry.getInvoiceId() != null ? " (invoice " + entry.getInvoiceId() + ")" : ""));
        }
        timeEntryRepo.delete(entry);
    }

    private TimeEntry findOwnTimeEntry(TenantId tenantId, UUID entryId) {
        return timeEntryRepo.findByTenantIdAndId(tenantId.getValue(), entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", entryId.toString()));
    }

    @Transactional
    public FeeNoteResponse generateFeeNote(TenantId tenantId, CreateFeeNoteRequest req) {
        findActive(tenantId, req.clientId());

        List<TimeEntry> entries = req.timeEntryIds().isEmpty()
                ? timeEntryRepo.findUnbilledByClient(req.clientId())
                : req.timeEntryIds().stream()
                .map(id -> timeEntryRepo.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", id.toString())))
                .toList();

        BigDecimal subtotal;
        if (req.fixedFee() != null) {
            subtotal = req.fixedFee();
        } else {
            subtotal = entries.stream()
                    .map(TimeEntry::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal vatAmount = req.includeVat()
                ? subtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String invoiceNumber = feeNoteNumberGen.next(tenantId);
        FeeNote feeNote = FeeNote.create(tenantId.getValue(), req.clientId(), invoiceNumber,
                req.invoiceDate(), req.dueDate(), subtotal, vatAmount);

        // Build line items from time entries
        int order = 0;
        for (TimeEntry e : entries) {
            FeeNoteLine line = buildFeeNoteLine(feeNote.getId(), e, req.includeVat(), order++);
            feeNote.getLines().add(line);
            e.markBilled(feeNote.getId());
            timeEntryRepo.save(e);
        }

        feeNoteRepo.save(feeNote);
        log.info("Generated fee note={} client={} amount={}", invoiceNumber, req.clientId(), feeNote.getTotal());
        return toFeeNoteResponse(feeNote);
    }

    @Transactional
    public FeeNoteResponse sendFeeNote(TenantId tenantId, UUID feeNoteId) {
        // FIX: was feeNoteRepo.findById(feeNoteId) — no tenant check at
        // all. See FeeNoteRepository.findByTenantIdAndId()'s own comment
        // for the full reasoning.
        FeeNote feeNote = feeNoteRepo.findByTenantIdAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeNote", feeNoteId.toString()));
        AccClient client = clientRepo.findById(feeNote.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", feeNote.getClientId().toString()));

        feeNote.markSent();
        feeNoteRepo.save(feeNote);

        if (client.getContactEmail() != null) {
            emailService.send(
                    client.getContactEmail(),
                    "Fee note " + feeNote.getInvoiceNumber() + " — " + client.getTradingName(),
                    za.co.handyflow.platform.shared.EmailTemplates.feeNote(
                            client.getTradingName(),
                            feeNote.getInvoiceNumber(),
                            feeNote.getTotal().toPlainString(),
                            feeNote.getDueDate().toString()));
        }

        return toFeeNoteResponse(feeNote);
    }

    /**
     * NEW: closes the #1 must-fix gap from the accountant module audit —
     * "billing has no money-in loop". Once sent, a fee note previously
     * had no path to ever being marked paid.
     */
    @Transactional
    public FeeNoteResponse recordPayment(TenantId tenantId, UUID feeNoteId, RecordPaymentRequest req,
                                         UUID recordedBy, String recordedByName) {
        FeeNote feeNote = feeNoteRepo.findByTenantIdAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeNote", feeNoteId.toString()));

        AccPaymentReceived payment = AccPaymentReceived.create(tenantId.getValue(), feeNoteId,
                feeNote.getClientId(), req.amount(), req.paymentDate(), req.paymentMethod(),
                req.reference(), req.notes(), recordedBy, recordedByName);
        paymentRepo.save(payment);

        BigDecimal totalPaid = paymentRepo.sumByFeeNoteId(feeNoteId);
        feeNote.applyPayment(totalPaid);
        feeNoteRepo.save(feeNote);

        log.info("Recorded payment R{} against fee note={} client={} — status now {}",
                req.amount(), feeNote.getInvoiceNumber(), feeNote.getClientId(), feeNote.getStatus());

        // NEW: closes gap #2 from the audit ("no invoice paid confirmation
        // to the client"). Only sent once actually fully PAID, not on a
        // partial payment.
        if ("PAID".equals(feeNote.getStatus())) {
            AccClient client = clientRepo.findById(feeNote.getClientId()).orElse(null);
            if (client != null && client.getContactEmail() != null) {
                emailService.send(
                        client.getContactEmail(),
                        "Payment received — " + feeNote.getInvoiceNumber() + " — " + client.getTradingName(),
                        za.co.handyflow.platform.shared.EmailTemplates.paymentReceived(
                                client.getTradingName(), feeNote.getInvoiceNumber(),
                                feeNote.getTotal().toPlainString(), req.paymentDate().toString()));
            }
        }

        return toFeeNoteResponse(feeNote);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(TenantId tenantId, UUID feeNoteId) {
        feeNoteRepo.findByTenantIdAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeNote", feeNoteId.toString()));
        return paymentRepo.findByFeeNoteId(feeNoteId).stream()
                .map(this::toPaymentResponse).toList();
    }

    /**
     * NEW: generates the Fee Note PDF — flagged in the module audit as a
     * quick win. Deliberately does not require a practice profile to
     * exist beyond what generateFeeNote()/sendFeeNote() already assume —
     * if AccountantProfile is genuinely absent (a tenant that's never
     * completed practice setup), this throws the same
     * ResourceNotFoundException pattern used everywhere else in this
     * service, rather than silently generating a document with a blank
     * firm name.
     */
    @Transactional(readOnly = true)
    public byte[] generateFeeNotePdf(TenantId tenantId, UUID feeNoteId) {
        FeeNote feeNote = feeNoteRepo.findByTenantIdAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeNote", feeNoteId.toString()));
        AccClient client = clientRepo.findActiveById(tenantId, feeNote.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", feeNote.getClientId().toString()));
        AccountantProfile profile = profileRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountantProfile", tenantId.getValue().toString()));

        return feeNotePdfGenerator.generate(feeNote, client, profile);
    }

    // ── FICA / KYC documents ────────────────────────────────────────────────
    // NEW: closes the accountant module audit's "document/attachment
    // storage on client records" gap. Every new client (ClientsTab) has
    // a FICA-completed checkbox but no way to attach the actual
    // documents proving it. Maps to acc_fica_documents — see
    // AccFicaDocument's own class Javadoc for why this table already
    // existed unused, and why storage_key is left alone rather than
    // repurposed.

    // Creative's equivalent upload path (base64-in-DB, same reasoning
    // as SCM's supplier invoice attachments — no S3 in this
    // environment) has no size check anywhere at all. Matching SCM's
    // own fix for that gap rather than repeating it here.
    private static final long MAX_FICA_DOC_BYTES = 10L * 1024 * 1024;

    // NEW: confirmed via a real upload (a .tsx source file accepted as
    // a "Trust Deed") that nothing restricted file types at all. A real
    // FICA/KYC document should be a scanned/photographed document, not
    // arbitrary file content — this is a data-quality guard, not a
    // security boundary (the file is only ever stored as a base64 blob
    // and never executed server-side either way).
    private static final java.util.Set<String> ALLOWED_FICA_DOC_TYPES = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Transactional
    public FicaDocumentResponse uploadFicaDocument(TenantId tenantId, UUID clientId,
                                                   UploadFicaDocumentRequest req,
                                                   UUID uploadedBy, String uploadedByName) {
        findActive(tenantId, clientId);

        String contentType = req.contentType() != null ? req.contentType() : "application/octet-stream";
        if (!ALLOWED_FICA_DOC_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type — FICA documents must be a PDF, JPG, PNG, or Word document");
        }

        long approxDecodedBytes = (req.fileContentBase64().length() * 3L) / 4;
        if (approxDecodedBytes > MAX_FICA_DOC_BYTES) {
            throw new IllegalArgumentException(
                    "File is too large — maximum attachment size is "
                            + (MAX_FICA_DOC_BYTES / (1024 * 1024)) + "MB");
        }

        AccFicaDocument doc = AccFicaDocument.create(tenantId.getValue(), clientId, req.docType(),
                req.fileName(), req.contentType() != null ? req.contentType() : "application/octet-stream",
                req.fileSizeBytes(), req.fileContentBase64(), req.expiryDate(), uploadedBy, uploadedByName,
                "STAFF");
        ficaDocRepo.save(doc);
        log.info("FICA document '{}' ({}) uploaded for client={}", req.fileName(), req.docType(), clientId);
        return toFicaDocumentResponse(doc);
    }

    @Transactional(readOnly = true)
    public List<FicaDocumentResponse> getFicaDocuments(TenantId tenantId, UUID clientId) {
        findActive(tenantId, clientId);
        // Uses the projection query — never fetches file_content_base64
        // for a list call. See FicaDocSummaryProjection's own comment.
        return ficaDocRepo.findSummariesByClient(tenantId.getValue(), clientId).stream()
                .map(p -> new FicaDocumentResponse(p.getId(), p.getDocType(), p.getFileName(),
                        p.getContentType(), p.getFileSizeBytes(), p.isVerified(), p.getVerifiedAt(),
                        p.getExpiryDate(), p.getUploadedByName(), p.getUploadedByType(), p.getCreatedAt()))
                .toList();
    }

    public record FicaDocFile(byte[] content, String contentType, String fileName) {}

    @Transactional(readOnly = true)
    public FicaDocFile downloadFicaDocument(TenantId tenantId, UUID clientId, UUID docId) {
        findActive(tenantId, clientId);
        AccFicaDocument doc = ficaDocRepo.findByTenantIdAndId(tenantId.getValue(), docId)
                .orElseThrow(() -> new ResourceNotFoundException("FicaDocument", docId.toString()));
        if (!doc.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("FicaDocument", docId.toString());
        }
        byte[] content = java.util.Base64.getDecoder().decode(doc.getFileContentBase64());
        String contentType = doc.getContentType() != null && !doc.getContentType().isBlank()
                ? doc.getContentType() : "application/octet-stream";
        return new FicaDocFile(content, contentType, doc.getFileName());
    }

    @Transactional
    public FicaDocumentResponse verifyFicaDocument(TenantId tenantId, UUID clientId, UUID docId, UUID verifiedBy) {
        findActive(tenantId, clientId);
        AccFicaDocument doc = ficaDocRepo.findByTenantIdAndId(tenantId.getValue(), docId)
                .orElseThrow(() -> new ResourceNotFoundException("FicaDocument", docId.toString()));
        if (!doc.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("FicaDocument", docId.toString());
        }
        doc.markVerified(verifiedBy);
        ficaDocRepo.save(doc);
        log.info("FICA document {} verified for client={} by={}", docId, clientId, verifiedBy);
        return toFicaDocumentResponse(doc);
    }

    @Transactional
    public void deleteFicaDocument(TenantId tenantId, UUID clientId, UUID docId) {
        findActive(tenantId, clientId);
        AccFicaDocument doc = ficaDocRepo.findByTenantIdAndId(tenantId.getValue(), docId)
                .orElseThrow(() -> new ResourceNotFoundException("FicaDocument", docId.toString()));
        if (!doc.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("FicaDocument", docId.toString());
        }
        ficaDocRepo.delete(doc);
    }

    private FicaDocumentResponse toFicaDocumentResponse(AccFicaDocument d) {
        return new FicaDocumentResponse(d.getId(), d.getDocType(), d.getFileName(), d.getContentType(),
                d.getFileSizeBytes(), d.isVerified(), d.getVerifiedAt(), d.getExpiryDate(),
                d.getUploadedByName(), d.getUploadedByType(), d.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<FeeNoteResponse> getOutstandingInvoices(TenantId tenantId) {
        return feeNoteRepo.findAllUnpaid(tenantId.getValue()).stream()
                .map(this::toFeeNoteResponse).toList();
    }

    // ── L7: Portfolio dashboard ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortfolioDashboardResponse getPortfolioDashboard(TenantId tenantId) {
        List<AccClient> clients = clientRepo.findAllActive(tenantId, Pageable.unpaged()).getContent();
        int total     = clients.size();
        int highRisk  = (int) clients.stream().filter(c -> "HIGH".equals(c.getRiskRating())).count();
        int ficaGap   = (int) clients.stream().filter(c -> !c.isFicaCompleted()).count();

        LocalDate now   = LocalDate.now();
        LocalDate in30  = now.plusDays(30);
        LocalDate in7   = now.plusDays(7);

        List<TaxDeadline> allDeadlines = deadlineRepo.findInDateRange(tenantId.getValue(), now, in30);
        int overdue    = (int) deadlineRepo.findOverdue(now).stream()
                .filter(d -> clients.stream().anyMatch(c -> c.getId().equals(d.getClientId()))).count();
        int upcoming   = allDeadlines.size();
        int thisMonth  = (int) allDeadlines.stream()
                .filter(d -> d.getAdjustedDueDate().getMonthValue() == now.getMonthValue()).count();

        List<TaxDeadlineResponse> urgent = deadlineRepo.findInDateRange(tenantId.getValue(), now, in7).stream()
                .map(this::toDeadlineResponse).toList();

        BigDecimal totalWip = clients.stream()
                .map(c -> timeEntryRepo.sumWipByClient(c.getId()))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FeeNote> outstanding = feeNoteRepo.findOutstanding(tenantId.getValue());
        BigDecimal totalOutstanding = outstanding.stream()
                .map(FeeNote::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioDashboardResponse(
                total, total, overdue, upcoming, thisMonth,
                totalWip, totalOutstanding,
                urgent,
                outstanding.stream().map(this::toFeeNoteResponse).toList(),
                highRisk, ficaGap);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AccClient findActive(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));
    }

    private ClientResponse toClientResponse(AccClient c, TenantId tenantId) {
        List<TaxDeadline> deadlines = deadlineRepo.findByClient(c.getId());
        int overdue = (int) deadlines.stream().filter(d -> "OVERDUE".equals(d.getStatus())).count();
        int open    = (int) deadlines.stream().filter(d -> "PENDING".equals(d.getStatus())).count();
        BigDecimal wip = timeEntryRepo.sumWipByClient(c.getId());
        BigDecimal outstanding = feeNoteRepo.findOutstanding(tenantId.getValue()).stream()
                .filter(f -> f.getClientId().equals(c.getId()))
                .map(FeeNote::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClientResponse(
                c.getId(), c.getEntityType(), c.getTradingName(), c.getRegisteredName(),
                c.getRegistrationNumber(), c.getTaxReferenceNumber(), c.getVatNumber(),
                c.getVatCategory(), c.getYearEndMonth(), c.getRiskRating(),
                c.isFicaCompleted(), c.isSarsAgentAppointed(), c.getTcsPin(), c.getTcsPinExpiry(),
                c.getOnboardingStatus(), c.getContactEmail(), c.getContactPhone(),
                open, overdue, wip != null ? wip : BigDecimal.ZERO, outstanding, c.getCreatedAt(),
                c.isClientDeadlineRemindersEnabled());
    }

    /**
     * NEW: closes the audit's "client-facing deadline reminder emails"
     * gap — the toggle behind the per-client opt-out.
     */
    @Transactional
    public ClientResponse setClientDeadlineRemindersEnabled(TenantId tenantId, UUID clientId, boolean enabled) {
        AccClient client = findActive(tenantId, clientId);
        client.setClientDeadlineRemindersEnabled(enabled);
        clientRepo.save(client);
        return toClientResponse(client, tenantId);
    }

    private TaxDeadlineResponse toDeadlineResponse(TaxDeadline d) {
        int days = (int) ChronoUnit.DAYS.between(LocalDate.now(), d.getAdjustedDueDate());
        // Fetch client name — intentionally simple (no join; called from portfolio view)
        String clientName = clientRepo.findById(d.getClientId())
                .map(AccClient::getTradingName).orElse("Unknown");
        return new TaxDeadlineResponse(
                d.getId(), d.getClientId(), clientName, d.getDeadlineType(),
                d.getPeriodYear(), d.getPeriodMonth(), d.getStatutoryDueDate(),
                d.getAdjustedDueDate(), d.getStatus(), d.getFiledDate(),
                d.getSarsReference(), d.getFilingAmount(), days, d.getNotes());
    }

    private JournalResponse toJournalResponse(AccJournal j) {
        // FIX: was hardcoded null/null with a "resolve via COA service
        // if needed" comment. Bulk lookup by the distinct account IDs
        // actually used on this journal's lines — one query per
        // journal, not one query per line.
        List<UUID> accountIds = j.getLines().stream().map(AccJournalLine::getAccountId).distinct().toList();
        Map<UUID, AccCoaAccount> accountsById = accountIds.isEmpty() ? Map.of()
                : coaAccountRepo.findByIdIn(accountIds).stream()
                .collect(java.util.stream.Collectors.toMap(AccCoaAccount::getId, a -> a));

        List<JournalLineResponse> lines = j.getLines().stream()
                .map(l -> {
                    AccCoaAccount acc = accountsById.get(l.getAccountId());
                    return new JournalLineResponse(l.getId(), l.getAccountId(),
                            acc != null ? acc.getAccountCode() : null,
                            acc != null ? acc.getAccountName() : null,
                            l.getDescription(), l.getDebit(), l.getCredit(),
                            l.getVatAmount(), l.getVatType(), l.getLineOrder());
                })
                .toList();
        return new JournalResponse(j.getId(), j.getClientId(), j.getPeriodId(),
                j.getReference(), j.getDescription(), j.getJournalType(), j.getStatus(),
                j.getJournalDate(), j.totalDebits(), j.totalCredits(), j.isBalanced(),
                lines, j.getCreatedAt());
    }

    private TimeEntryResponse toTimeEntryResponse(TimeEntry t) {
        return new TimeEntryResponse(t.getId(), t.getClientId(), t.getPractitionerId(),
                t.getEntryDate(), t.getActivityType(), t.getDescription(),
                t.getHours(), t.getHourlyRate(), t.lineTotal(),
                t.isBillable(), t.getStatus(), t.getInvoiceId(), t.getCreatedAt());
    }

    private FeeNoteResponse toFeeNoteResponse(FeeNote f) {
        // FIX: was hardcoded BigDecimal.ZERO with a "// TODO: sum from
        // acc_payments_received" comment — now genuinely computed from
        // real payment records.
        BigDecimal paid = paymentRepo.sumByFeeNoteId(f.getId());
        BigDecimal balance = f.getTotal().subtract(paid);
        int daysOverdue = f.getDueDate().isBefore(LocalDate.now()) && !"PAID".equals(f.getStatus())
                ? (int) ChronoUnit.DAYS.between(f.getDueDate(), LocalDate.now()) : 0;
        String clientName = clientRepo.findById(f.getClientId())
                .map(AccClient::getTradingName).orElse("Unknown");
        List<FeeNoteLineResponse> lines = f.getLines().stream()
                .map(l -> new FeeNoteLineResponse(l.getId(), l.getDescription(),
                        l.getQuantity(), l.getUnitPrice(), l.getVatRate(), l.getAmount()))
                .toList();
        return new FeeNoteResponse(f.getId(), f.getClientId(), clientName, f.getInvoiceNumber(),
                f.getInvoiceDate(), f.getDueDate(), f.getSubtotal(), f.getVatAmount(),
                f.getTotal(), paid, balance, f.getStatus(), daysOverdue, lines, f.getCreatedAt());
    }

    private PaymentResponse toPaymentResponse(AccPaymentReceived p) {
        return new PaymentResponse(p.getId(), p.getFeeNoteId(), p.getAmount(), p.getPaymentDate(),
                p.getPaymentMethod(), p.getReference(), p.getNotes(), p.getRecordedByName(), p.getCreatedAt());
    }

    private FeeNoteLine buildFeeNoteLine(UUID feeNoteId, TimeEntry e, boolean includeVat, int order) {
        return FeeNoteLine.forTimeEntry(feeNoteId, e, includeVat, order);
    }

    // ── Practice profile ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(TenantId tenantId) {
        return profileRepo.findByTenantId(tenantId)
                .map(this::toProfileResponse)
                .orElse(null);   // null = no profile yet; frontend shows setup prompt
    }

    @Transactional
    public ProfileResponse upsertProfile(TenantId tenantId, CreateProfileRequest req) {
        AccountantProfile profile = profileRepo.findByTenantId(tenantId)
                .orElse(null);
        if (profile == null) {
            profile = AccountantProfile.create(tenantId, req.firmName(), req.practiceNumber(),
                    req.vatNumber(), req.contactEmail(), req.contactPhone(),
                    req.defaultHourlyRate(), req.yearEndMonth());
        } else {
            profile.update(req.firmName(), req.practiceNumber(), req.vatNumber(),
                    req.contactEmail(), req.contactPhone(),
                    req.defaultHourlyRate(), req.yearEndMonth());
        }
        profileRepo.save(profile);
        return toProfileResponse(profile);
    }

    private ProfileResponse toProfileResponse(AccountantProfile p) {
        return new ProfileResponse(p.getId(), p.getFirmName(), p.getPracticeNumber(),
                p.getRegistrationNumber(), p.getVatNumber(), p.getContactEmail(),
                p.getContactPhone(), p.getDefaultHourlyRate(), p.getYearEndMonth(),
                p.getCreatedAt());
    }
}