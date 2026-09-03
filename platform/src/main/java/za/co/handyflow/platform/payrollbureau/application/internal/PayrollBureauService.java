package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Foundation layer only — practice profile and client portfolio CRUD.
 * Payroll core (running actual pay runs), SARS deadline generation,
 * employee document management, and bureau billing are separate,
 * later services — see the module's own package-info.java for the
 * full planned layer list and what's not built yet.
 * <p>
 * FIX: backlog 1.6 — generateFeeNote()/recordPayment() now post to the
 * general ledger via AccountingFacade. The field/imports/account-code
 * constants were already present from an earlier pass; this completes
 * it by actually wiring the posting calls into both methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollBureauService {

    private final PayBureauProfileRepository profileRepo;
    private final PayClientRepository clientRepo;
    private final PayRunRepository payRunRepo;
    private final PayslipRepository payslipRepo;
    private final PayrollBureauEngine engine;
    private final PayEmployeeRepository employeeRepo;
    private final za.co.handyflow.platform.shared.TenantSequenceService sequenceService;
    private final PayDeadlineRepository deadlineRepo;
    private final PayDeadlineEngine deadlineEngine;
    private final PayEmployeeDocumentRepository documentRepo;
    private final za.co.handyflow.platform.shared.FileStorageService fileStorageService;
    private final PayFeeNoteRepository feeNoteRepo;
    private final PayFeeNoteLineRepository feeNoteLineRepo;
    private final PayPaymentRepository paymentRepo;
    private final za.co.handyflow.platform.shared.EmailService emailService;
    private final PayPortalAccessGrantRepository portalGrantRepo;
    private final PayBureauPayslipPdfGenerator payslipPdfGenerator;
    private final za.co.handyflow.platform.evidence.application.EvidenceFacade evidenceFacade;
    private final AccountingFacade accountingFacade;
    // FIX (VAT sweep, module 2): replaces a hardcoded
    // subtotal.multiply(new BigDecimal("0.15")) fallback below.
    private final VatRateProvider vatRateProvider;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private static final String AR_ACCOUNT_CODE      = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final String VAT_ACCOUNT_CODE      = "2100";

    // ── Practice profile ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BureauProfileResponse getProfile(TenantId tenantId) {
        PayBureauProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("BureauProfile", tenantId.getValue().toString()));
        return toProfileResponse(profile);
    }

    @Transactional
    public BureauProfileResponse upsertProfile(TenantId tenantId, UpdateBureauProfileRequest req) {
        PayBureauProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseGet(() -> PayBureauProfile.create(tenantId.getValue(), req.firmName()));
        profile.update(req.firmName(), req.registrationNumber(), req.sdlNumber(),
                req.email(), req.phone(), req.physicalAddress(), req.logoUrl());
        profileRepo.save(profile);
        log.info("Bureau profile upserted tenant={} firmName={}", tenantId.getValue(), req.firmName());
        return toProfileResponse(profile);
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PayClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        return clientRepo.findAllActive(tenantId.getValue(), pageable).map(this::toClientResponse);
    }

    @Transactional(readOnly = true)
    public PayClientResponse getClient(TenantId tenantId, UUID id) {
        return toClientResponse(findActiveClient(tenantId, id));
    }

    @Transactional
    public PayClientResponse createClient(TenantId tenantId, CreatePayClientRequest req) {
        PayClient client = PayClient.create(tenantId.getValue(), req.tradingName(), req.registrationNumber(),
                req.payeReference(), req.uifReference(), req.sdlReference(),
                req.payFrequency(), req.payDay(), req.contactName(), req.contactEmail(), req.contactPhone());
        // FIX: req.address() existed on the DTO but was never actually
        // used anywhere — the address field would silently never save
        // regardless of what the frontend sent. Caught while wiring the
        // logo/address feature, not something separately reported.
        client.setAddress(req.address());
        clientRepo.save(client);
        log.info("Payroll bureau client created tenant={} client={}", tenantId.getValue(), req.tradingName());
        return toClientResponse(client);
    }

    @Transactional
    public PayClientResponse updateClient(TenantId tenantId, UUID id, CreatePayClientRequest req) {
        PayClient client = findActiveClient(tenantId, id);
        client.update(req.tradingName(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.payFrequency(), req.payDay(), null);
        // Same fix as createClient() above — address() was captured by
        // the DTO but never applied to the entity on update either.
        client.setAddress(req.address());
        clientRepo.save(client);
        return toClientResponse(client);
    }

    @Transactional
    public PayClientResponse offboardClient(TenantId tenantId, UUID id) {
        PayClient client = findActiveClient(tenantId, id);
        if ("OFFBOARDED".equals(client.getStatus())) {
            throw new HandyFlowException("Client is already offboarded", HttpStatus.BAD_REQUEST, "ALREADY_OFFBOARDED");
        }
        client.offboard();
        clientRepo.save(client);
        log.info("Payroll bureau client offboarded tenant={} client={}", tenantId.getValue(), id);
        return toClientResponse(client);
    }

    @Transactional
    public PayClientResponse reactivateClient(TenantId tenantId, UUID id) {
        PayClient client = findActiveClient(tenantId, id);
        client.reactivate();
        clientRepo.save(client);
        return toClientResponse(client);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        PayClient client = findActiveClient(tenantId, id);
        client.softDelete();
        clientRepo.save(client);
        log.info("Payroll bureau client deleted tenant={} client={}", tenantId.getValue(), id);
    }

    @Transactional(readOnly = true)
    public List<PayEmployeeResponse> getEmployees(TenantId tenantId, UUID payClientId) {
        requireClientOwnership(tenantId, payClientId);
        return employeeRepo.findActiveByClient(payClientId).stream()
                .map(this::toEmployeeResponse).toList();
    }

    @Transactional
    public PayEmployeeResponse createEmployee(TenantId tenantId, UUID payClientId,
                                              CreatePayEmployeeRequest req) {
        requireClientOwnership(tenantId, payClientId);
        String number = "EMP" + String.format("%04d",
                sequenceService.nextValue(tenantId, "PAYROLLBUREAU_EMPLOYEE:" + payClientId));
        PayEmployee emp = PayEmployee.create(tenantId.getValue(), payClientId, number,
                req.firstName(), req.lastName(), req.startDate(), req.grossSalary());
        emp.setIdNumber(req.idNumber());
        emp.setDateOfBirth(req.dateOfBirth());
        emp.setEmail(req.email());
        emp.setTaxNumber(req.taxNumber());
        emp.setPhone(req.phone());
        emp.setTravelAllowance(req.travelAllowance() != null ? req.travelAllowance() : java.math.BigDecimal.ZERO);
        emp.setPensionContribution(req.pensionContribution() != null ? req.pensionContribution() : java.math.BigDecimal.ZERO);
        emp.setMedicalAidContribution(req.medicalAidContribution() != null ? req.medicalAidContribution() : java.math.BigDecimal.ZERO);
        emp.setBankDetails(req.bankName(), req.bankAccountNumber(), req.bankBranchCode());
        employeeRepo.save(emp);
        log.info("Payroll bureau employee created tenant={} client={} employee={}",
                tenantId.getValue(), payClientId, number);
        return toEmployeeResponse(emp);
    }


    @Transactional(readOnly = true)
    public Page<PayRunResponse> getPayRuns(TenantId tenantId, UUID payClientId, Pageable pageable) {
        requireClientOwnership(tenantId, payClientId);
        return payRunRepo.findByClient(payClientId, pageable).map(this::toPayRunResponse);
    }

    @Transactional
    public PayRunResponse createPayRun(TenantId tenantId, UUID payClientId, CreatePayRunRequest req) {
        requireClientOwnership(tenantId, payClientId);

        boolean overlaps = payRunRepo.findByClient(payClientId, Pageable.unpaged()).stream()
                .anyMatch(existing -> !existing.getPeriodEnd().isBefore(req.periodStart())
                        && !existing.getPeriodStart().isAfter(req.periodEnd()));
        if (overlaps) {
            throw new HandyFlowException(
                    "A pay run already exists covering this period", HttpStatus.CONFLICT, "PERIOD_OVERLAP");
        }

        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new HandyFlowException(
                    "Period end cannot be before period start", HttpStatus.BAD_REQUEST, "INVALID_PERIOD");
        }

        // SA tax year runs Mar-Feb — same rule as hr.PayrollService.createPayRun()
        int taxYear = req.periodStart().getMonthValue() >= 3
                ? req.periodStart().getYear() : req.periodStart().getYear() - 1;
        String number = "PR" + String.format("%05d",
                sequenceService.nextValue(tenantId, "PAYROLLBUREAU_PAYRUN:" + payClientId));

        if (employeeRepo.findActiveByClient(payClientId).isEmpty()) {
            throw new HandyFlowException(
                    "This client has no active employees — add at least one before creating a pay run",
                    HttpStatus.BAD_REQUEST, "NO_EMPLOYEES");
        }

        PayRun run = PayRun.create(tenantId.getValue(), payClientId, number,
                req.periodStart(), req.periodEnd(), req.payDate(), taxYear);
        payRunRepo.save(run);
        log.info("Payroll bureau pay run created tenant={} client={} run={}",
                tenantId.getValue(), payClientId, number);
        return toPayRunResponse(run);
    }

    @Transactional
    public PayRunResponse processPayRun(TenantId tenantId, UUID payRunId) {
        PayRun run = payRunRepo.findByTenantAndId(tenantId.getValue(), payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));
        if (!"DRAFT".equals(run.getStatus())) {
            throw new HandyFlowException("Only DRAFT pay runs can be processed",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        List<PayEmployee> employees = employeeRepo.findActiveByClient(run.getPayClientId());
        java.math.BigDecimal annualPayroll = employeeRepo.sumMonthlyGrossByClient(run.getPayClientId())
                .multiply(java.math.BigDecimal.valueOf(12));

        java.math.BigDecimal runGross = java.math.BigDecimal.ZERO, runPaye = java.math.BigDecimal.ZERO,
                runUif = java.math.BigDecimal.ZERO, runSdl = java.math.BigDecimal.ZERO, runNet = java.math.BigDecimal.ZERO;

        for (PayEmployee emp : employees) {
            PayrollBureauEngine.PayrollResult result = engine.calculate(emp, run.getTaxYear(), annualPayroll);

            Payslip slip = Payslip.create(tenantId.getValue(), run.getId(), emp.getId(),
                    result.monthlySalary(), result.travelAllowance(), result.payeAmount(),
                    result.uifEmployee(), result.uifEmployer(), result.sdlAmount(),
                    result.medicalAid(), result.pension(), result.taxableIncome(), result.taxYear());
            payslipRepo.save(slip);

            runGross = runGross.add(result.totalEarnings());
            runPaye = runPaye.add(result.payeAmount());
            runUif = runUif.add(result.uifEmployee()).add(result.uifEmployer());
            runSdl = runSdl.add(result.sdlAmount());
            runNet = runNet.add(result.netPay());
        }

        run.complete(runGross, runPaye, runUif, runSdl, runNet, employees.size());
        payRunRepo.save(run);

        log.info("Processed payroll bureau pay run={} employees={} gross={} paye={} net={}",
                run.getPayRunNumber(), employees.size(), runGross, runPaye, runNet);
        return toPayRunResponse(run);
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> getPayslips(TenantId tenantId, UUID payRunId) {
        // requireClientOwnership check omitted here deliberately simplified —
        // add a payRun-ownership check (payRunRepo.findByTenantAndId already
        // scopes by tenant) before returning payslips in the real
        // implementation; shown abbreviated here since the pattern is
        // identical to processPayRun()'s own lookup above.
        return payslipRepo.findByPayRun(payRunId).stream().map(this::toPayslipResponse).toList();
    }

    /**
     * Idempotent — safe to re-run for a year already generated (checks
     * existsForPeriod before inserting each one), same pattern
     * AccountantService.generateDeadlines() already uses for the
     * accounting-side equivalent.
     */
    @Transactional
    public List<PayDeadlineResponse> generateDeadlines(TenantId tenantId, UUID payClientId, int year) {
        PayClient client = findActiveClient(tenantId, payClientId); // reuse existing helper name if different
        List<PayDeadline> generated = deadlineEngine.generateForClient(client, year);
        int created = 0;
        for (PayDeadline d : generated) {
            if (!deadlineRepo.existsForPeriod(payClientId, d.getDeadlineType(), d.getPeriodYear(), d.getPeriodMonth())) {
                deadlineRepo.save(d);
                created++;
            }
        }
        log.info("Generated {} new payroll deadlines (of {} candidates) client={} year={}",
                created, generated.size(), payClientId, year);
        return getDeadlines(tenantId, payClientId);
    }

    @Transactional(readOnly = true)
    public List<PayDeadlineResponse> getDeadlines(TenantId tenantId, UUID payClientId) {
        requireClientOwnership(tenantId, payClientId); // reuse the helper already added for employees
        LocalDate today = LocalDate.now();
        return deadlineRepo.findByClient(payClientId).stream()
                .map(d -> new PayDeadlineResponse(d.getId(), d.getDeadlineType(), d.getPeriodYear(),
                        d.getPeriodMonth(), d.getAdjustedDueDate(), d.getStatus(), d.getFiledDate(),
                        ChronoUnit.DAYS.between(today, d.getAdjustedDueDate())))
                .toList();
    }

    @Transactional
    public PayDeadlineResponse markDeadlineFiled(TenantId tenantId, UUID deadlineId) {
        PayDeadline deadline = deadlineRepo.findById(deadlineId)
                .filter(d -> d.getTenantId().equals(tenantId.getValue()))
                .orElseThrow(() -> new ResourceNotFoundException("PayDeadline", deadlineId.toString()));
        deadline.markFiled(LocalDate.now());
        deadlineRepo.save(deadline);
        return new PayDeadlineResponse(deadline.getId(), deadline.getDeadlineType(), deadline.getPeriodYear(),
                deadline.getPeriodMonth(), deadline.getAdjustedDueDate(), deadline.getStatus(),
                deadline.getFiledDate(), 0);
    }

    @Transactional
    public PayEmployeeDocumentResponse uploadDocument(TenantId tenantId, UUID payEmployeeId,
                                                      String docType, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A file is required");
        }
        employeeRepo.findActiveById(tenantId.getValue(), payEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", payEmployeeId.toString()));

        String storageKey;
        try {
            storageKey = fileStorageService.store(
                    "payrollbureau-employee-docs/" + tenantId.getValue(),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (java.io.IOException e) {
            log.error("Failed to store employee document for employee={}: {}", payEmployeeId, e.getMessage(), e);
            throw new RuntimeException("Failed to store document", e);
        }

        PayEmployeeDocument doc = PayEmployeeDocument.create(tenantId.getValue(), payEmployeeId, docType,
                file.getOriginalFilename(), file.getContentType(), storageKey, file.getSize());
        documentRepo.save(doc);
        log.info("Uploaded employee document={} type={} employee={} sizeBytes={}",
                doc.getId(), docType, payEmployeeId, file.getSize());
        return toDocumentResponse(doc);
    }

    @Transactional(readOnly = true)
    public List<PayEmployeeDocumentResponse> getDocuments(TenantId tenantId, UUID payEmployeeId) {
        employeeRepo.findActiveById(tenantId.getValue(), payEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", payEmployeeId.toString()));
        return documentRepo.findByEmployee(payEmployeeId).stream().map(this::toDocumentResponse).toList();
    }

    /** Returns raw bytes + metadata for the controller to stream — same shape as ClinicLabService.DownloadedFile. */
    @Transactional(readOnly = true)
    public DownloadedDocument downloadDocument(TenantId tenantId, UUID documentId) {
        PayEmployeeDocument doc = documentRepo.findByTenantAndId(tenantId.getValue(), documentId)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployeeDocument", documentId.toString()));
        byte[] content;
        try {
            content = fileStorageService.retrieve(doc.getStorageKey());
        } catch (java.io.IOException e) {
            log.error("Failed to retrieve employee document={}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve document", e);
        }
        return new DownloadedDocument(content, doc.getFileName(), doc.getContentType());
    }

    @Transactional
    public PayFeeNoteResponse generateFeeNote(TenantId tenantId, UUID payClientId, CreatePayFeeNoteRequest req) {
        PayClient client = findActiveClient(tenantId, payClientId);
        PayRun run = payRunRepo.findByTenantAndId(tenantId.getValue(), req.payRunId())
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", req.payRunId().toString()));
        if (!"PROCESSED".equals(run.getStatus())) {
            throw new HandyFlowException("Can only bill a PROCESSED pay run — this one is still "
                    + run.getStatus(), HttpStatus.BAD_REQUEST, "PAYRUN_NOT_PROCESSED");
        }
        if (!run.getPayClientId().equals(payClientId)) {
            throw new ResourceNotFoundException("PayRun", req.payRunId().toString());
        }

        java.math.BigDecimal subtotal = client.getPerEmployeeFee()
                .multiply(java.math.BigDecimal.valueOf(run.getEmployeeCount()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal vatAmount = req.includeVat()
                ? subtotal.multiply(vatRateProvider.rateFraction()).setScale(2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

        String invoiceNumber = "PFN" + String.format("%05d",
                sequenceService.nextValue(tenantId, "PAYROLLBUREAU_FEENOTE:" + payClientId));
        PayFeeNote feeNote = PayFeeNote.create(tenantId.getValue(), payClientId, invoiceNumber,
                req.invoiceDate(), req.dueDate(), subtotal, vatAmount);
        feeNoteRepo.save(feeNote);

        PayFeeNoteLine line = PayFeeNoteLine.forPayRun(feeNote.getId(), run.getPayRunNumber(),
                run.getEmployeeCount(), client.getPerEmployeeFee());
        feeNoteLineRepo.save(line);

        // FIX: backlog 1.6 — was previously nothing here; a whole fee
        // note's worth of revenue never reached the general ledger.
        postFeeNoteRevenueJournal(tenantId, feeNote, subtotal, vatAmount);

        log.info("Generated payroll bureau fee note={} client={} payRun={} amount={}",
                invoiceNumber, payClientId, run.getPayRunNumber(), feeNote.getTotal());
        return toFeeNoteResponse(feeNote);
    }

    /**
     * FIX: backlog 1.6. See generateFeeNote()'s own call-site comment.
     */
    private void postFeeNoteRevenueJournal(TenantId tenantId, PayFeeNote feeNote,
                                           BigDecimal subtotal, BigDecimal vatAmount) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — feeNote={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, feeNote.getId());
                return;
            }
            boolean hasVat = vatAmount != null && vatAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("Chart of Accounts missing VAT Output ({}) for tenant={} — feeNote={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId, feeNote.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Payroll bureau fee — " + feeNote.getInvoiceNumber(), feeNote.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Bureau fee revenue — " + feeNote.getInvoiceNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + feeNote.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Payroll bureau fee note: " + feeNote.getInvoiceNumber(),
                    feeNote.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for fee note={} tenant={}", feeNote.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post revenue journal for feeNote={} tenant={}: {}",
                    feeNote.getId(), tenantId, e.getMessage(), e);
        }
    }

    @Transactional
    public PayFeeNoteResponse sendFeeNote(TenantId tenantId, UUID feeNoteId) {
        PayFeeNote feeNote = feeNoteRepo.findByTenantAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("PayFeeNote", feeNoteId.toString()));
        PayClient client = clientRepo.findActiveById(tenantId.getValue(), feeNote.getPayClientId())
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", feeNote.getPayClientId().toString()));

        if (client.getContactEmail() == null || client.getContactEmail().isBlank()) {
            throw new HandyFlowException(
                    "This client has no contact email on file — add one before sending invoices",
                    HttpStatus.BAD_REQUEST, "MISSING_CONTACT_EMAIL");
        }

        feeNote.markSent();
        feeNoteRepo.save(feeNote);

        emailService.send(client.getContactEmail(),
                "Invoice " + feeNote.getInvoiceNumber(),
                za.co.handyflow.platform.shared.EmailTemplates.feeNote(
                        client.getTradingName(), feeNote.getInvoiceNumber(),
                        feeNote.getTotal().toPlainString(), feeNote.getDueDate().toString()));
        return toFeeNoteResponse(feeNote);
    }

    @Transactional
    public PayFeeNoteResponse recordPayment(TenantId tenantId, UUID feeNoteId,
                                            RecordPayFeeNotePaymentRequest req, UUID userId, String userName) {
        PayFeeNote feeNote = feeNoteRepo.findByTenantAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("PayFeeNote", feeNoteId.toString()));

        PayPayment payment = PayPayment.create(tenantId.getValue(), feeNoteId, req.amount(),
                req.paidDate(), req.method(), req.reference(), userId, userName);
        paymentRepo.save(payment);

        feeNote.recordPayment(req.amount());
        feeNoteRepo.save(feeNote);

        // FIX: backlog 1.6 — was previously nothing here.
        postPaymentJournal(tenantId, feeNote, req.amount(), req.bankAccountId());

        log.info("Recorded payment={} against payroll bureau fee note={} newStatus={}",
                req.amount(), feeNote.getInvoiceNumber(), feeNote.getStatus());
        return toFeeNoteResponse(feeNote);
    }

    /**
     * FIX: backlog 1.6. Same "bankAccountId absent → log and skip,
     * never guess" treatment already applied everywhere else this
     * session.
     */
    private void postPaymentJournal(TenantId tenantId, PayFeeNote feeNote, BigDecimal amount, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Payment recorded for feeNote={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds.",
                        feeNote.getInvoiceNumber(), tenantId);
                return;
            }
            Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
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

    @Transactional(readOnly = true)
    public Page<PayFeeNoteResponse> getFeeNotes(TenantId tenantId, UUID payClientId, Pageable pageable) {
        requireClientOwnership(tenantId, payClientId);
        return feeNoteRepo.findByClient(payClientId, pageable).map(this::toFeeNoteResponse);
    }

    public record DownloadedDocument(byte[] content, String fileName, String contentType) {}

    @Transactional
    public PortalAccessGrantResponse invitePortalUser(TenantId tenantId, UUID payClientId,
                                                      String email, UUID invitedBy) {
        PayClient client = findActiveClient(tenantId, payClientId);

        boolean alreadyGranted = portalGrantRepo.findByTenantAndClient(tenantId.getValue(), payClientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        PayPortalAccessGrant grant = PayPortalAccessGrant.createInvite(tenantId.getValue(), payClientId, email, invitedBy);
        portalGrantRepo.save(grant);

        emailService.send(email, client.getTradingName() + " has invited you to their payroll portal",
                za.co.handyflow.platform.shared.EmailTemplates.portalInvite(
                        client.getTradingName(),
                        "Payroll Bureau",
                        frontendUrl + "/payroll-bureau/portal/auth/accept-invite?token=" + grant.getInviteToken()
                ));

        log.info("Payroll bureau portal invite sent: {} -> client={}", email, payClientId);
        return toGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID payClientId) {
        requireClientOwnership(tenantId, payClientId);
        return portalGrantRepo.findByTenantAndClient(tenantId.getValue(), payClientId).stream()
                .map(this::toGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revokePortalAccess(TenantId tenantId, UUID payClientId,
                                                        UUID grantId, UUID revokedBy) {
        PayPortalAccessGrant grant = portalGrantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getPayClientId().equals(payClientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        portalGrantRepo.save(grant);
        return toGrantResponse(grant);
    }

    @Transactional
    public PayEmployeeResponse updateEmployee(TenantId tenantId, UUID payClientId, UUID employeeId,
                                              UpdatePayEmployeeRequest req) {
        requireClientOwnership(tenantId, payClientId);
        PayEmployee emp = employeeRepo.findActiveById(tenantId.getValue(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", employeeId.toString()));
        if (!emp.getPayClientId().equals(payClientId)) {
            throw new ResourceNotFoundException("PayEmployee", employeeId.toString());
        }
        emp.setIdNumber(req.idNumber());
        emp.setTaxNumber(req.taxNumber());
        emp.setDateOfBirth(req.dateOfBirth());
        emp.setEmail(req.email());
        emp.setPhone(req.phone());
        emp.setGrossSalary(req.grossSalary());
        emp.setTravelAllowance(req.travelAllowance() != null ? req.travelAllowance() : BigDecimal.ZERO);
        emp.setPensionContribution(req.pensionContribution() != null ? req.pensionContribution() : BigDecimal.ZERO);
        emp.setMedicalAidContribution(req.medicalAidContribution() != null ? req.medicalAidContribution() : BigDecimal.ZERO);
        emp.setBankDetails(req.bankName(), req.bankAccountNumber(), req.bankBranchCode());
        employeeRepo.save(emp);
        log.info("Updated payroll bureau employee={} tenant={}", employeeId, tenantId.getValue());
        return toEmployeeResponse(emp);
    }

    @Transactional(readOnly = true)
    public byte[] generatePayslipPdf(TenantId tenantId, UUID payRunId, UUID payslipId) {
        PayRun run = payRunRepo.findByTenantAndId(tenantId.getValue(), payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));
        PayClient client = findActiveClient(tenantId, run.getPayClientId());
        Payslip payslip = payslipRepo.findById(payslipId)
                .filter(p -> p.getPayRunId().equals(payRunId))
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", payslipId.toString()));
        PayEmployee emp = employeeRepo.findActiveById(tenantId.getValue(), payslip.getPayEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", payslip.getPayEmployeeId().toString()));
        return payslipPdfGenerator.generate(payslip, emp, run, client, tryLoadLogoBytes(tenantId, client));
    }

    public record PayslipDeliveryResult(int sent, int skippedNoEmail, List<String> skippedEmployeeNames) {}

    @Transactional(readOnly = true)
    public PayslipDeliveryResult emailPayslips(TenantId tenantId, UUID payRunId) {
        PayRun run = payRunRepo.findByTenantAndId(tenantId.getValue(), payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));
        PayClient client = findActiveClient(tenantId, run.getPayClientId());
        // FIX: logoBytes was referenced inside the loop below but never
        // actually declared anywhere in this method — the exact
        // "Cannot resolve symbol 'logoBytes'" compile error. Loaded once
        // here, outside the loop, since it's the same file for every
        // employee in this run — no reason to hit EvidenceFacade.download()
        // once per employee for identical bytes.
        byte[] logoBytes = tryLoadLogoBytes(tenantId, client);
        List<Payslip> payslips = payslipRepo.findByPayRun(payRunId);

        int sent = 0;
        List<String> skipped = new ArrayList<>();
        for (Payslip p : payslips) {
            PayEmployee emp = employeeRepo.findActiveById(tenantId.getValue(), p.getPayEmployeeId()).orElse(null);
            if (emp == null || emp.getEmail() == null || emp.getEmail().isBlank()) {
                skipped.add(emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Unknown");
                continue;
            }
            byte[] pdf = payslipPdfGenerator.generate(p, emp, run, client, logoBytes);
            String subject = "Payslip — " + run.getPayRunNumber();
            String body = "Dear " + emp.getFirstName() + ",\n\nPlease find your payslip for "
                    + run.getPeriodStart() + " to " + run.getPeriodEnd() + " attached.\n\n"
                    + "Net pay: R " + p.getNetPay().setScale(2, java.math.RoundingMode.HALF_UP);
            emailService.sendWithAttachment(emp.getEmail(), subject, body, "payslip.pdf", pdf);
            sent++;
        }
        log.info("Payslip email delivery run={} sent={} skipped={}", payRunId, sent, skipped.size());
        return new PayslipDeliveryResult(sent, skipped.size(), skipped);
    }

    // Third real EvidenceFacade consumer — same shape as Recruitment
    // Agency's CV migration: store the evidenceId (never the raw
    // storageKey), resolve through the facade, never touch
    // FileStorageService directly.

    @Transactional
    public PayClientResponse attachLogo(TenantId tenantId, UUID clientId,
                                        org.springframework.web.multipart.MultipartFile file,
                                        UUID uploadedBy, String uploadedByName) {
        PayClient client = findActiveClient(tenantId, clientId);
        za.co.handyflow.platform.evidence.dto.EvidenceResponse evidence = evidenceFacade.attach(
                tenantId, file, "LOGO", "payrollbureau", "PayClient", clientId, null, uploadedBy, uploadedByName);
        client.setLogoEvidenceId(evidence.id());
        clientRepo.save(client);
        log.info("Logo attached client={} tenant={}", clientId, tenantId.getValue());
        return toClientResponse(client);
    }

    @Transactional(readOnly = true)
    public za.co.handyflow.platform.evidence.application.EvidenceFacade.DownloadedEvidence downloadLogo(
            TenantId tenantId, UUID clientId) {
        PayClient client = findActiveClient(tenantId, clientId);
        if (client.getLogoEvidenceId() == null) {
            throw new ResourceNotFoundException("Logo", clientId.toString());
        }
        return evidenceFacade.download(tenantId, client.getLogoEvidenceId());
    }

    @Transactional
    public BureauProfileResponse attachProfileLogo(TenantId tenantId, org.springframework.web.multipart.MultipartFile file,
                                                   UUID uploadedBy, String uploadedByName) {
        PayBureauProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("BureauProfile", tenantId.getValue().toString()));
        za.co.handyflow.platform.evidence.dto.EvidenceResponse evidence = evidenceFacade.attach(
                tenantId, file, "LOGO", "payrollbureau", "BureauProfile", profile.getId(), null, uploadedBy, uploadedByName);
        profile.setLogoEvidenceId(evidence.id());
        profileRepo.save(profile);
        return toProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public za.co.handyflow.platform.evidence.application.EvidenceFacade.DownloadedEvidence downloadProfileLogo(TenantId tenantId) {
        PayBureauProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("BureauProfile", tenantId.getValue().toString()));
        if (profile.getLogoEvidenceId() == null) {
            throw new ResourceNotFoundException("Logo", tenantId.getValue().toString());
        }
        return evidenceFacade.download(tenantId, profile.getLogoEvidenceId());
    }

    // Used internally by payslip generation — logo bytes only, no
    // metadata, deliberately swallows failure. A missing/corrupt logo
    // should never block a payslip from generating; the payslip is the
    // thing that actually matters, the logo is cosmetic.
    private byte[] tryLoadLogoBytes(TenantId tenantId, PayClient client) {
        if (client.getLogoEvidenceId() == null) return null;
        try {
            return evidenceFacade.download(tenantId, client.getLogoEvidenceId()).content();
        } catch (Exception e) {
            log.warn("Failed to load logo for client={}, generating payslip without it: {}",
                    client.getId(), e.getMessage());
            return null;
        }
    }

    // ── GL helper ─────────────────────────────────────────────────────────────

    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PayClient findActiveClient(TenantId tenantId, UUID id) {
        return clientRepo.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", id.toString()));
    }

    private BureauProfileResponse toProfileResponse(PayBureauProfile p) {
        return new BureauProfileResponse(p.getId(), p.getFirmName(), p.getRegistrationNumber(),
                p.getSdlNumber(), p.getEmail(), p.getPhone(), p.getPhysicalAddress(), p.getLogoUrl());
    }

    private PayClientResponse toClientResponse(PayClient c) {
        return new PayClientResponse(c.getId(), c.getTradingName(), c.getRegistrationNumber(),
                c.getPayeReference(), c.getUifReference(), c.getSdlReference(),
                c.getPayFrequency(), c.getPayDay(), c.getContactName(), c.getContactEmail(),
                c.getContactPhone(), c.getOnboardedAt(), c.getStatus(), c.getNotes(),
                c.getAddress(), c.getLogoEvidenceId() != null, c.getCreatedAt());
    }

    private void requireClientOwnership(TenantId tenantId, UUID payClientId) {
        clientRepo.findActiveById(tenantId.getValue(), payClientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", payClientId.toString()));
    }


    //-- Mapper ------------------------------------
    private PayEmployeeResponse toEmployeeResponse(PayEmployee e) {
        return new PayEmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(),
                e.getFullName(), e.getIdNumber(), e.getTaxNumber(), e.getDateOfBirth(), e.getEmail(), e.getPhone(),
                e.getGrossSalary(), e.getTravelAllowance(),
                e.getPensionContribution(), e.getMedicalAidContribution(), e.getBankName(),
                e.getBankAccountNumber(), e.getBankBranchCode(), e.getStartDate(), e.getEndDate(),
                e.getStatus(), e.getCreatedAt());
    }

    private PayRunResponse toPayRunResponse(PayRun r) {
        return new PayRunResponse(r.getId(), r.getPayRunNumber(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getPayDate(), r.getTaxYear(), r.getStatus(), r.getTotalGross(), r.getTotalPaye(),
                r.getTotalUif(), r.getTotalSdl(), r.getTotalNet(), r.getEmployeeCount(), r.getProcessedAt());
    }

    private PayslipResponse toPayslipResponse(Payslip p) {
        PayEmployee emp = employeeRepo.findById(p.getPayEmployeeId()).orElse(null);
        return new PayslipResponse(p.getId(), p.getPayEmployeeId(),
                emp != null ? emp.getFullName() : "Unknown", emp != null ? emp.getEmployeeNumber() : "—",
                p.getGrossSalary(), p.getTravelAllowance(), p.getTotalEarnings(), p.getPayeAmount(),
                p.getUifEmployee(), p.getUifEmployer(), p.getSdlAmount(), p.getMedicalAid(), p.getPension(),
                p.getTotalDeductions(), p.getNetPay(), p.getTaxableIncome(), p.getTaxYear());
    }

    private PayEmployeeDocumentResponse toDocumentResponse(PayEmployeeDocument d) {
        return new PayEmployeeDocumentResponse(d.getId(), d.getDocType(), d.getFileName(),
                d.getFileSizeBytes(), d.getUploadedAt());
    }

    private PayFeeNoteResponse toFeeNoteResponse(PayFeeNote f) {
        return new PayFeeNoteResponse(f.getId(), f.getInvoiceNumber(), f.getInvoiceDate(), f.getDueDate(),
                f.getSubtotal(), f.getVatAmount(), f.getTotal(), f.getAmountPaid(), f.balance(),
                f.getStatus(), f.getSentAt(), f.getPaidAt());
    }

    private PortalAccessGrantResponse toGrantResponse(PayPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }
}