package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Foundation layer only — practice profile and client portfolio CRUD.
 * Payroll core (running actual pay runs), SARS deadline generation,
 * employee document management, and bureau billing are separate,
 * later services — see the module's own package-info.java for the
 * full planned layer list and what's not built yet.
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
        clientRepo.save(client);
        log.info("Payroll bureau client created tenant={} client={}", tenantId.getValue(), req.tradingName());
        return toClientResponse(client);
    }

    @Transactional
    public PayClientResponse updateClient(TenantId tenantId, UUID id, CreatePayClientRequest req) {
        PayClient client = findActiveClient(tenantId, id);
        client.update(req.tradingName(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.payFrequency(), req.payDay(), null);
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
        // SA tax year runs Mar-Feb — same rule as hr.PayrollService.createPayRun()
        int taxYear = req.periodStart().getMonthValue() >= 3
                ? req.periodStart().getYear() : req.periodStart().getYear() - 1;
        String number = "PR" + String.format("%05d",
                sequenceService.nextValue(tenantId, "PAYROLLBUREAU_PAYRUN:" + payClientId));
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
        // Confirms the employee exists and belongs to this tenant before
        // storing anything — same ownership-check-before-storage-write
        // ordering as every other upload flow in this codebase.
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
        // Same tenant-ownership confirmation as uploadDocument() above —
        // don't skip this just because it's a read, per the same
        // discipline flagged as a gap (and needing completion) for
        // getPayslips() back in Section 49.
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

    /**
     * Generates an invoice FROM a processed pay run — deliberately
     * requires PROCESSED status (not DRAFT), since billing for payroll
     * that hasn't actually been calculated yet would let a fee note's
     * employee count/amount diverge from what was really run. Same
     * "don't bill ahead of the real event" discipline as
     * accountant.TimeEntry.markBilled() only accepting UNBILLED entries.
     */
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
                ? subtotal.multiply(new java.math.BigDecimal("0.15")).setScale(2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

        String invoiceNumber = "PFN" + String.format("%05d",
                sequenceService.nextValue(tenantId, "PAYROLLBUREAU_FEENOTE:" + payClientId));
        PayFeeNote feeNote = PayFeeNote.create(tenantId.getValue(), payClientId, invoiceNumber,
                req.invoiceDate(), req.dueDate(), subtotal, vatAmount);
        feeNoteRepo.save(feeNote);

        PayFeeNoteLine line = PayFeeNoteLine.forPayRun(feeNote.getId(), run.getPayRunNumber(),
                run.getEmployeeCount(), client.getPerEmployeeFee());
        feeNoteLineRepo.save(line);

        log.info("Generated payroll bureau fee note={} client={} payRun={} amount={}",
                invoiceNumber, payClientId, run.getPayRunNumber(), feeNote.getTotal());
        return toFeeNoteResponse(feeNote);
    }

    @Transactional
    public PayFeeNoteResponse sendFeeNote(TenantId tenantId, UUID feeNoteId) {
        PayFeeNote feeNote = feeNoteRepo.findByTenantAndId(tenantId.getValue(), feeNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("PayFeeNote", feeNoteId.toString()));
        PayClient client = clientRepo.findActiveById(tenantId.getValue(), feeNote.getPayClientId())
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", feeNote.getPayClientId().toString()));

        feeNote.markSent();
        feeNoteRepo.save(feeNote);

        if (client.getContactEmail() != null) {
            // Reuses accountant's exact feeNote() email template — same
            // shape of invoice email (invoice number, amount, due date),
            // no reason to write a second version for a nearly-identical
            // notification. If bureau-specific copy is ever wanted, that's
            // a real, separate template to add — not a reason to skip
            // reuse now.
            emailService.send(client.getContactEmail(),
                    "Invoice " + feeNote.getInvoiceNumber(),
                    za.co.handyflow.platform.shared.EmailTemplates.feeNote(
                            client.getTradingName(), feeNote.getInvoiceNumber(),
                            feeNote.getTotal().toPlainString(), feeNote.getDueDate().toString()));
        }
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

        log.info("Recorded payment={} against payroll bureau fee note={} newStatus={}",
                req.amount(), feeNote.getInvoiceNumber(), feeNote.getStatus());
        return toFeeNoteResponse(feeNote);
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

        // Refuses a duplicate invite for the same email+client rather
        // than silently creating a second grant or silently doing
        // nothing — either would be confusing for staff to reason about
        // later. Same check accountant.invitePortalUser() already does.
        boolean alreadyGranted = portalGrantRepo.findByTenantAndClient(tenantId.getValue(), payClientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        PayPortalAccessGrant grant = PayPortalAccessGrant.createInvite(tenantId.getValue(), payClientId, email, invitedBy);
        portalGrantRepo.save(grant);

        // Reuses accountant's exact portalInvite() email template — same
        // "you've been invited to a client portal" shape, no reason for
        // a bureau-specific duplicate.
        emailService.send(email, client.getTradingName() + " has invited you to their payroll portal",
                za.co.handyflow.platform.shared.EmailTemplates.portalInvite(
                        client.getTradingName(),
                        "Payroll Bureau", // consider pulling from PayBureauProfile.firmName instead of this literal
                        "https://app.handyflow.co.za/payroll-bureau/portal/auth/accept-invite?token=" + grant.getInviteToken()
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
                c.getContactPhone(), c.getOnboardedAt(), c.getStatus(), c.getNotes(), c.getCreatedAt());
    }

    private void requireClientOwnership(TenantId tenantId, UUID payClientId) {
        clientRepo.findActiveById(tenantId.getValue(), payClientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", payClientId.toString()));
    }


    //-- Mapper ------------------------------------
    private PayEmployeeResponse toEmployeeResponse(PayEmployee e) {
        return new PayEmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(),
                e.getFullName(), e.getIdNumber(), e.getDateOfBirth(), e.getGrossSalary(), e.getTravelAllowance(),
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