package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.*;
import za.co.handyflow.platform.accountant.domain.repository.*;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
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
    private final AccPaymentReceivedRepository paymentRepo;
    private final AccFeeNotePdfGenerator   feeNotePdfGenerator;
    private final AccFicaDocumentRepository ficaDocRepo;
    private final AccPeriodRepository       periodRepo;
    private final AccCoaAccountRepository   coaAccountRepo;
    private final AccJournalLineRepository  journalLineRepo;
    private final AccPortalAccessGrantRepository portalGrantRepo;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final AccountingFacade accountingFacade;

    private static final String AR_ACCOUNT_CODE      = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final String VAT_ACCOUNT_CODE      = "2100";

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

        if (req.sendWelcomeEmail()) {
            if (client.getContactEmail() != null && !client.getContactEmail().isBlank()) {
                String firmName = profileRepo.findByTenantId(tenantId)
                        .map(AccountantProfile::getFirmName)
                        .orElse("your accountant");
                emailService.send(client.getContactEmail(),
                        "Welcome to " + firmName,
                        za.co.handyflow.platform.shared.EmailTemplates.clientOnboardingWelcome(
                                client.getTradingName(), firmName, client.getContactEmail()));
            } else {
                log.warn("sendWelcomeEmail=true for client={} but no contact email on file — no email sent",
                        client.getId());
            }
        }

        return toClientResponse(client, tenantId);
    }

    @Transactional
    public ClientResponse updateClient(TenantId tenantId, UUID clientId, UpdateClientRequest req) {
        AccClient client = findActive(tenantId, clientId);
        if (req.riskRating() != null) client.updateRisk(req.riskRating());
        clientRepo.save(client);
        return toClientResponse(client, tenantId);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID clientId) {
        AccClient client = findActive(tenantId, clientId);
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

    @Transactional
    public List<TaxDeadlineResponse> generateDeadlines(TenantId tenantId, UUID clientId, int year) {
        AccClient client = findActive(tenantId, clientId);
        List<TaxDeadline> generated = deadlineEngine.generateForClient(client, year);
        generated.forEach(d -> {
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
        findActive(tenantId, clientId);
        TaxDeadline deadline = deadlineRepo.findByClient(clientId).stream()
                .filter(d -> d.getId().equals(deadlineId))
                .findFirst()
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

        BigDecimal debits  = req.lines().stream()
                .map(l -> l.debit()  != null ? l.debit()  : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = req.lines().stream()
                .map(l -> l.credit() != null ? l.credit() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0)
            throw new HandyFlowException(
                    String.format("Journal is not balanced: debits R%s ≠ credits R%s", debits, credits),
                    HttpStatus.BAD_REQUEST, "JOURNAL_UNBALANCED");

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
        AccJournal journal = journalRepo.findByTenantIdAndId(tenantId.getValue(), journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal", journalId.toString()));
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

    @Transactional(readOnly = true)
    public Page<JournalResponse> getClientJournals(TenantId tenantId, UUID clientId, Pageable pageable) {
        clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));
        return journalRepo.findByClient(clientId, pageable).map(this::toJournalResponse);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(TenantId tenantId, UUID clientId, int periodYear, int periodMonth) {
        clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId.toString()));

        List<AccCoaAccount> accounts = coaAccountRepo.findActiveByClient(clientId);

        Map<UUID, AccJournalLineRepository.AccountBalanceRow> before =
                journalLineRepo.sumByAccountBeforePeriod(clientId, periodYear, periodMonth).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AccJournalLineRepository.AccountBalanceRow::getAccountId, r -> r));

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

    @Transactional(readOnly = true)
    public Page<FeeNoteResponse> getClientFeeNotes(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActive(tenantId, clientId);
        return feeNoteRepo.findByClient(clientId, pageable).map(this::toFeeNoteResponse);
    }

    @Transactional(readOnly = true)
    public Page<TimeEntryResponse> getClientTimeEntries(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActive(tenantId, clientId);
        return timeEntryRepo.findByClient(clientId, pageable).map(this::toTimeEntryResponse);
    }

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

    @Transactional
    public PortalAccessGrantResponse invitePortalUser(TenantId tenantId, UUID clientId,
                                                      String email, UUID invitedBy) {
        AccClient client = findActive(tenantId, clientId);

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
        String acceptUrl = frontendUrl + "/accountant/portal/auth/accept-invite?token=" + grant.getInviteToken();

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
    public TimeEntryResponse logTime(TenantId tenantId, CreateTimeEntryRequest req,
                                     UUID practitionerId, String practitionerName) {
        findActive(tenantId, req.clientId());
        TimeEntry entry = TimeEntry.create(tenantId.getValue(), req.clientId(), practitionerId, practitionerName,
                req.entryDate(), req.activityType(), req.description(),
                req.hours(), req.hourlyRate(), req.billable());
        timeEntryRepo.save(entry);
        return toTimeEntryResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<StaffTimeSummaryResponse> getStaffTimeSummary(TenantId tenantId, LocalDate from, LocalDate to) {
        return timeEntryRepo.findStaffSummary(tenantId.getValue(), from, to).stream()
                .map(p -> new StaffTimeSummaryResponse(
                        p.getPractitionerId(),
                        p.getPractitionerId() == null ? "Unassigned" : (p.getPractitionerName() != null ? p.getPractitionerName() : "Unknown"),
                        p.getTotalHours(), p.getBillableHours(), p.getTotalBilled(), p.getEntryCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> getUnbilledTime(TenantId tenantId, UUID clientId) {
        return timeEntryRepo.findUnbilledByClient(clientId).stream()
                .map(this::toTimeEntryResponse).toList();
    }

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

        int order = 0;
        for (TimeEntry e : entries) {
            FeeNoteLine line = buildFeeNoteLine(feeNote.getId(), e, req.includeVat(), order++);
            feeNote.getLines().add(line);
            e.markBilled(feeNote.getId());
            timeEntryRepo.save(e);
        }

        feeNoteRepo.save(feeNote);

        postFeeNoteRevenueJournal(tenantId, feeNote, subtotal, vatAmount);

        log.info("Generated fee note={} client={} amount={}", invoiceNumber, req.clientId(), feeNote.getTotal());
        return toFeeNoteResponse(feeNote);
    }

    @Transactional
    public FeeNoteResponse sendFeeNote(TenantId tenantId, UUID feeNoteId) {
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

        // FIX: backlog 1.6 — was previously nothing here.
        postPaymentJournal(tenantId, feeNote, req.amount(), req.bankAccountId());

        log.info("Recorded payment R{} against fee note={} client={} — status now {}",
                req.amount(), feeNote.getInvoiceNumber(), feeNote.getClientId(), feeNote.getStatus());

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

    private static final long MAX_FICA_DOC_BYTES = 10L * 1024 * 1024;

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

    @Transactional
    public ClientResponse setClientDeadlineRemindersEnabled(TenantId tenantId, UUID clientId, boolean enabled) {
        AccClient client = findActive(tenantId, clientId);
        client.setClientDeadlineRemindersEnabled(enabled);
        clientRepo.save(client);
        return toClientResponse(client, tenantId);
    }

    private TaxDeadlineResponse toDeadlineResponse(TaxDeadline d) {
        int days = (int) ChronoUnit.DAYS.between(LocalDate.now(), d.getAdjustedDueDate());
        String clientName = clientRepo.findById(d.getClientId())
                .map(AccClient::getTradingName).orElse("Unknown");
        return new TaxDeadlineResponse(
                d.getId(), d.getClientId(), clientName, d.getDeadlineType(),
                d.getPeriodYear(), d.getPeriodMonth(), d.getStatutoryDueDate(),
                d.getAdjustedDueDate(), d.getStatus(), d.getFiledDate(),
                d.getSarsReference(), d.getFilingAmount(), days, d.getNotes());
    }

    private JournalResponse toJournalResponse(AccJournal j) {
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

    private void postFeeNoteRevenueJournal(TenantId tenantId, FeeNote feeNote,
                                           java.math.BigDecimal subtotal, java.math.BigDecimal vatAmount) {
        try {
            java.util.UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            java.util.UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — feeNote={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, feeNote.getId());
                return;
            }
            boolean hasVat = vatAmount != null && vatAmount.compareTo(java.math.BigDecimal.ZERO) > 0;
            java.util.UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("Chart of Accounts missing VAT Output ({}) for tenant={} — feeNote={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId, feeNote.getId());
                    return;
                }
            }

            java.util.List<CreateJournalEntryRequest.JournalLineRequest> lines = new java.util.ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Accountant fee — " + feeNote.getInvoiceNumber(), feeNote.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Fee revenue — " + feeNote.getInvoiceNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + feeNote.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    java.time.LocalDate.now(), "Accountant fee note: " + feeNote.getInvoiceNumber(),
                    feeNote.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for fee note={} tenant={}", feeNote.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post revenue journal for feeNote={} tenant={}: {}",
                    feeNote.getId(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * FIX: backlog 1.6. Same "bankAccountId absent → log and skip,
     * never guess" treatment already applied everywhere else this
     * session. Was missing entirely — recordPayment() had no path to
     * the general ledger at all before this.
     */
    private void postPaymentJournal(TenantId tenantId, FeeNote feeNote, BigDecimal amount, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Payment recorded for feeNote={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds.",
                        feeNote.getInvoiceNumber(), tenantId);
                return;
            }
            java.util.Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
            if (bankGl.isEmpty()) {
                log.warn("Bank account={} for tenant={} not found or not linked — payment for feeNote={} not posted",
                        bankAccountId, tenantId, feeNote.getInvoiceNumber());
                return;
            }
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("Chart of Accounts missing AR ({}) for tenant={} — payment not posted", AR_ACCOUNT_CODE, tenantId);
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankGl.get(), "Payment received — " + feeNote.getInvoiceNumber(), amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Payment received — " + feeNote.getInvoiceNumber(), null, amount));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Payment received: " + feeNote.getInvoiceNumber(),
                    feeNote.getInvoiceNumber(), "PAYMENT", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted payment journal for feeNote={} tenant={}", feeNote.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post payment journal for feeNote={} tenant={}: {}",
                    feeNote.getId(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * FIX: backlog 1.6 — was referenced by postFeeNoteRevenueJournal()
     * (already present from an earlier pass) but never actually
     * defined anywhere in this file — a live "cannot resolve method"
     * compile error until this was added.
     */
    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    // ── Practice profile ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(TenantId tenantId) {
        return profileRepo.findByTenantId(tenantId)
                .map(this::toProfileResponse)
                .orElse(null);
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