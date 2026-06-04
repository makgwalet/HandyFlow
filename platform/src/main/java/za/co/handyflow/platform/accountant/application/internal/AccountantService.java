package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
import java.util.List;
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

        // TODO: validate period not locked (check acc_periods.status)

        AccJournal journal = AccJournal.create(tenantId.getValue(), clientId,
                req.periodId(), req.reference(), req.description(),
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
        AccJournal journal = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal", journalId.toString()));
        journal.submitForReview(reviewer);
        journalRepo.save(journal);
        return toJournalResponse(journal);
    }

    @Transactional
    public JournalResponse postJournal(TenantId tenantId, UUID clientId, UUID journalId, UUID approver) {
        AccJournal journal = journalRepo.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal", journalId.toString()));
        journal.approve(approver);
        journal.post();
        journalRepo.save(journal);
        log.info("Posted journal={} client={}", journal.getReference(), clientId);
        return toJournalResponse(journal);
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
        FeeNote feeNote = feeNoteRepo.findById(feeNoteId)
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
                open, overdue, wip != null ? wip : BigDecimal.ZERO, outstanding, c.getCreatedAt());
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
        List<JournalLineResponse> lines = j.getLines().stream()
                .map(l -> new JournalLineResponse(l.getId(), l.getAccountId(),
                        null, null,   // account code/name: resolve via COA service if needed
                        l.getDescription(), l.getDebit(), l.getCredit(),
                        l.getVatAmount(), l.getVatType(), l.getLineOrder()))
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
        // Compute amount paid from payments received — simplified sum
        BigDecimal paid = BigDecimal.ZERO; // TODO: sum from acc_payments_received
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
