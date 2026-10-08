package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.ClinicMedicalAidRepository;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.billing.*;
import za.co.handyflow.platform.clinic.dto.billing.BatchSubmitClaimsResponse.BatchSubmitResult;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicBillingService {

    private final ClinicClaimRepository              claimRepo;
    private final ClinicConsultationRepository       consultationRepo;
    private final ClinicPatientRepository            patientRepo;
    private final ClinicPractitionerRepository       practitionerRepo;
    private final ClinicPrescriptionRepository       prescriptionRepo;
    private final ClinicMedicationCatalogueRepository medicationRepo;
    private final ClinicMedicalAidRepository           medicalAidRepo;
    private final EmailService                        emailService;
    private final ClinicPatientInvoicePdfService       patientInvoicePdfService;
    private final ClinicPaymentRepository              paymentRepo;
    // FIX: backlog 1.6 — the shared AccountingFacade. Direct call, no
    // event indirection — confirmed no circular dependency between
    // clinic and accounting.
    private final AccountingFacade accountingFacade;
    // Scheme money (0150): payments, write-offs, credit notes and voids live in an append-only ledger.
    private final ClinicClaimMoneyService  money;
    private final ClinicClaimLedgerService ledger;

    // Real, confirmed seeded codes from ChartOfAccountsSeeder — not
    // invented, same codes already used by Invoicing/POS's own GL
    // posting this session.
    private static final String AR_ACCOUNT_CODE      = "1100"; // Accounts Receivable
    private static final String REVENUE_ACCOUNT_CODE = "4000"; // Revenue

    // Deliberately NO VAT account/line anywhere in this class's GL
    // posting — medical consultations are commonly VAT-exempt under
    // South African law, and nothing in this module's billing DTOs
    // carries a vatAmount field at all. Fabricating a VAT split with no
    // evidence this clinic charges it would be a worse error than
    // omitting it — if this clinic DOES charge VAT on some services,
    // that's a real, separate design decision needing its own
    // confirmation, not something to guess at here.

    // ── Create claim from consultation ────────────────────────────────────────

    @Transactional
    public ClinicClaimResponse createClaim(TenantId tenantId, UUID consultationId,
                                           CreateClaimRequest req) {
        if (claimRepo.findByConsultation(tenantId, consultationId).isPresent())
            throw new IllegalStateException("A claim already exists for consultation " + consultationId);

        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));

        // FIX #11 — Auto-fill scheme details from stored medical aid record.
        // If the request provides them explicitly, use those; otherwise look up
        // the patient's (or their principal's) active medical aid record.
        String schemeName   = req.schemeName();
        String memberNumber = req.memberNumber();
        String dependentCode = req.dependentCode();

        if (schemeName == null || memberNumber == null) {
            // Patient's own record first; a dependant falls back to their principal's.
            var aidRecords = medicalAidRepo.findActiveByPatient(tenantId, c.getPatientId());
            if (aidRecords.isEmpty()) {
                var patient = patientRepo.findActiveById(tenantId, c.getPatientId()).orElse(null);
                if (patient != null && patient.getPrincipalId() != null) {
                    aidRecords = medicalAidRepo.findActiveByPatient(tenantId, patient.getPrincipalId());
                }
            }
            if (!aidRecords.isEmpty()) {
                var aid = aidRecords.get(0);
                // Request values take precedence; only fill the gaps.
                if (schemeName    == null) schemeName    = aid.getSchemeName();
                if (memberNumber  == null) memberNumber  = aid.getMemberNumber();
                if (dependentCode == null) dependentCode = aid.getDependentCode();
            }
        }

        ClinicClaim claim = ClinicClaim.create(tenantId, consultationId,
                c.getPatientId(), c.getPractitionerId(),
                schemeName, memberNumber, dependentCode);
        claimRepo.save(claim);

        // ── Consultation tariff line ───────────────────────────────────────────
        if (req.consultationTariffCode() != null) {
            ClinicClaimLine line = ClinicClaimLine.of(
                    claim.getId(), "CONSULTATION",
                    req.consultationTariffCode(), null,
                    req.consultationIcd10Code(),
                    req.consultationDescription() != null ? req.consultationDescription() : "Consultation",
                    BigDecimal.ONE, req.consultationRate(), null, 0);
            claim.addLine(line);
        }

        // ── Procedure lines ───────────────────────────────────────────────────
        if (req.procedures() != null) {
            int procOrder = 10; // FIX #5 — renamed from `order` to avoid duplicate declaration
            for (var proc : req.procedures()) {
                ClinicClaimLine line = ClinicClaimLine.of(
                        claim.getId(), "PROCEDURE",
                        proc.tariffCode(), null, proc.icd10Code(),
                        proc.description(), proc.quantity(), proc.unitPrice(), null, procOrder);
                claim.addLine(line);
                procOrder += 10;
            }
        }

        // ── Medicine lines (from consultation prescriptions) ──────────────────
        List<ClinicPrescription> rxList = prescriptionRepo.findByConsultation(tenantId, consultationId);
        int medicineOrder = 100; // FIX #5 — was also named `order`, causing compile error
        for (ClinicPrescription rx : rxList) {
            BigDecimal unitPrice = rx.getNappiCode() != null
                    ? medicationRepo.findBySep(rx.getNappiCode(), tenantId).orElse(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            ClinicClaimLine line = ClinicClaimLine.of(
                    claim.getId(), "MEDICINE",
                    null, rx.getNappiCode(),
                    c.getIcd10Codes() != null && !c.getIcd10Codes().isEmpty()
                            ? c.getIcd10Codes().get(0) : null,
                    rx.getMedicationName(),
                    BigDecimal.valueOf(rx.getQuantity() != null ? rx.getQuantity() : 1),
                    unitPrice, rx.getId(), medicineOrder);
            claim.addLine(line);
            medicineOrder += 10;
        }

        // Scheme claims: the scheme is expected to pay, so the patient is not billed the
        // gross up front. Real splits arrive via markPaid/markPartial (audit F-09).
        if (schemeName != null && !schemeName.isBlank()
                && memberNumber != null && !memberNumber.isBlank()) {
            claim.expectSchemeToCover();
        }
        claim.recalculate();
        claimRepo.save(claim);

        // FIX #9 — mark the source consultation as billed so it stops appearing
        // in the "unbilled consultations" picker (findAllUnbilled) in ClaimsTab.
        // Without this, a doctor could accidentally create two claims for one consultation
        // (the unique index on consultation_id catches it at DB level with a 500, not a UI warning).
        c.markBilled(req.consultationTariffCode(),
                req.consultationRate() != null ? req.consultationRate() : BigDecimal.ZERO);
        consultationRepo.save(c);

        // FIX: backlog 1.6 — was previously nothing here; a whole
        // claim's worth of revenue never reached the general ledger.
        // Debit AR for the full grossAmount, credit Revenue for the
        // same amount — no VAT split (see the class-level comment on
        // why), and no split between scheme-portion/patient-portion on
        // the AR side either: both are owed to the practice regardless
        // of who pays, so a single combined receivable is the correct,
        // simple treatment here.
        postClaimRevenueJournal(tenantId, claim);

        log.info("Created claim={} consultation={} gross={}", claim.getId(), consultationId, claim.getGrossAmount());
        return toResponse(claim, tenantId);
    }

    /**
     * FIX: backlog 1.6. See createClaim()'s own call-site comment for
     * the full rationale.
     */
    private void postClaimRevenueJournal(TenantId tenantId, ClinicClaim claim) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — claim={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, claim.getId());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Claim revenue — patient portion + scheme portion",
                            claim.getGrossAmount(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Consultation/claim revenue",
                            null, claim.getGrossAmount()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Clinic claim created: " + claim.getId(),
                    claim.getId().toString(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for claim={} tenant={} amount={}",
                    claim.getId(), tenantId, claim.getGrossAmount());
        } catch (Exception e) {
            // Claim is already saved by the time this runs — a posting
            // failure must never look like it affected that.
            log.error("Failed to post revenue journal for claim={} tenant={}: {}",
                    claim.getId(), tenantId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public ClinicClaimResponse getClaim(TenantId tenantId, UUID consultationId) {
        return claimRepo.findByConsultation(tenantId, consultationId)
                .map(c -> toResponse(c, tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Claim for consultation", consultationId.toString()));
    }

    /** One claim by its own id, with ledger figures (the claim list and consultation lookup use other keys). */
    @Transactional(readOnly = true)
    public ClinicClaimResponse getClaimById(TenantId tenantId, UUID claimId) {
        return toResponse(claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString())), tenantId);
    }

    // IMPROVEMENT A — batch-load patient/practitioner names so ClaimsTab can display them
    @Transactional(readOnly = true)
    public List<ClinicClaimResponse> getClaims(TenantId tenantId, String status) {
        List<ClinicClaim> claims = status != null
                ? claimRepo.findByStatus(tenantId, status)
                : claimRepo.findAll(tenantId);
        return withNames(tenantId, claims);
    }

    /** Patch 0178: one page of claims, newest first. Size is held between 1 and 100. */
    @Transactional(readOnly = true)
    public ClaimListDtos.ClaimPage getClaimPage(TenantId tenantId, String status, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        var result = (status != null && !status.isBlank())
                ? claimRepo.findPageByStatus(tenantId, status, pageable)
                : claimRepo.findPage(tenantId, pageable);
        return new ClaimListDtos.ClaimPage(withNames(tenantId, result.getContent()),
                result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
    }

    /** Patch 0178: the figures above the claims list, over every claim of the status. */
    @Transactional(readOnly = true)
    public ClaimListDtos.ClaimSummary getClaimSummary(TenantId tenantId, String status) {
        List<Object[]> rows = (status != null && !status.isBlank())
                ? claimRepo.summaryFactsByStatus(tenantId, status)
                : claimRepo.summaryFacts(tenantId);
        Map<UUID, ClaimLedgerRules.Totals> totals = ledger.totalsByClaim(tenantId.getValue());
        List<ClaimSummaryRules.Fact> facts = rows.stream()
                .map(r -> new ClaimSummaryRules.Fact((String) r[1], (java.math.BigDecimal) r[2],
                        totals.getOrDefault((UUID) r[0], ClaimLedgerRules.Totals.NONE)))
                .toList();
        var s = ClaimSummaryRules.summarise(facts);
        return new ClaimListDtos.ClaimSummary(s.total(), s.outstanding(), s.paid(), s.rejected());
    }

    private List<ClinicClaimResponse> withNames(TenantId tenantId, List<ClinicClaim> claims) {
        // Batch-load names — avoids N+1
        Set<UUID> patientIds     = claims.stream().map(ClinicClaim::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> practitionerIds = claims.stream().map(ClinicClaim::getPractitionerId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));
        Map<UUID, String> practNames = practitionerIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practitionerIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, ClinicPractitioner::getFullName));

        Map<UUID, ClaimLedgerRules.Totals> ledgerTotals = ledger.totalsByClaim(tenantId.getValue());
        return claims.stream()
                .map(c -> toResponseWithNames(c, patientNames, practNames, ledgerTotals))
                .toList();
    }

    @Transactional
    public ClinicClaimResponse submitClaim(TenantId tenantId, UUID claimId, String referenceNumber) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));

        // FIX Gap B — Claim readiness validation before submission.
        // Schemes reject claims with missing ICD-10 codes on any line — catch this here
        // rather than letting it fail silently at the medical aid switch.
        var linesWithoutIcd10 = claim.getLines().stream()
                .filter(l -> l.getIcd10Code() == null || l.getIcd10Code().isBlank())
                .map(l -> l.getDescription() + " (" + l.getLineType() + ")")
                .toList();

        if (!linesWithoutIcd10.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot submit: the following claim lines are missing ICD-10 codes: " +
                            String.join(", ", linesWithoutIcd10));
        }

        // Require at least one line
        if (claim.getLines().isEmpty()) {
            throw new IllegalStateException("Cannot submit a claim with no billing lines");
        }

        claim.submit(referenceNumber);
        claimRepo.save(claim);
        log.info("Submitted claim={} ref={} lines={}", claimId, referenceNumber, claim.getLines().size());
        return toResponse(claim, tenantId);
    }

    @Transactional
    public ClinicClaimResponse updateClaimStatus(TenantId tenantId, UUID claimId,
                                                 String action, String reason,
                                                 java.math.BigDecimal schemeAmount) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        String upperAction = action.toUpperCase();
        if (Set.of("ACCEPT", "REJECT", "PAID", "PARTIAL").contains(upperAction)) {
            ClaimTransitions.require(upperAction, claim.getStatus());
        }
        if ("REJECT".equals(upperAction) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Give the reason the scheme rejected the claim");
        }
        switch (upperAction) {
            case "ACCEPT"  -> { claim.markAccepted(); claimRepo.save(claim); }
            case "REJECT"  -> { claim.markRejected(reason); claimRepo.save(claim); }
            // CLINIC-DEC-001: Mark paid receives the remaining balance, whatever amount the caller sent.
            case "PAID"    -> claim = money.markPaid(tenantId, claimId, null);
            case "PARTIAL" -> claim = money.partial(tenantId, claimId, schemeAmount, null);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }

        // FIX: "no claim status-change notification" gap — the patient
        // previously found out about an accepted/rejected/paid/partial claim
        // only if someone told them manually. Never let a notification
        // failure surface as a failure of the status update itself.
        notifyPatientOfStatusChange(tenantId, claim, upperAction);

        return toResponse(claim, tenantId);
    }

    private void notifyPatientOfStatusChange(TenantId tenantId, ClinicClaim claim, String action) {
        try {
            ClinicPatient patient = patientRepo.findActiveById(tenantId, claim.getPatientId()).orElse(null);
            if (patient == null || patient.getEmail() == null || patient.getEmail().isBlank()) {
                return;
            }

            String subject;
            String bodyMessage;
            switch (action) {
                case "ACCEPT" -> {
                    subject = "Your medical aid claim was accepted";
                    bodyMessage = "Good news — your medical aid scheme has accepted this claim.";
                }
                case "REJECT" -> {
                    subject = "Your medical aid claim was rejected";
                    bodyMessage = "Unfortunately your medical aid scheme rejected this claim"
                            + (claim.getRejectionReason() != null ? ": " + claim.getRejectionReason() : ".");
                }
                case "PAID" -> {
                    subject = "Your medical aid claim was paid";
                    bodyMessage = "Your medical aid scheme has paid this claim in full.";
                }
                case "PARTIAL" -> {
                    subject = "Your medical aid claim was partially paid";
                    bodyMessage = "Your medical aid scheme has paid part of this claim. "
                            + "The practice will contact you if any balance is your responsibility.";
                }
                default -> { return; }
            }

            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>" + bodyMessage + "</p>"
                    + "<p>Claim amount: R " + String.format(java.util.Locale.US, "%,.2f", claim.getGrossAmount()) + "</p>"
                    + "<p>If you have any questions, please contact the practice.</p>";

            // FIX: this email previously said "please see your account for
            // details" without providing any — ClinicPatientInvoicePdfService
            // (the exact document showing that balance) was only reachable
            // through its own separate download endpoint, never attached
            // here. Only attach when there's an actual patient-portion
            // balance to show — same gating ClaimsTab.tsx already uses for
            // its own "Patient invoice" download button, so a fully
            // scheme-covered claim doesn't get a redundant "R0.00 due" PDF.
            if (claim.getPatientPortion() != null && claim.getPatientPortion().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    byte[] pdfBytes = patientInvoicePdfService.generate(tenantId, claim.getId());
                    emailService.sendWithAttachment(patient.getEmail(), subject, html,
                            "patient-invoice-" + claim.getId() + ".pdf", pdfBytes);
                } catch (Exception pdfEx) {
                    log.warn("Patient invoice PDF not attached for claim={}, sending without it: {}",
                            claim.getId(), pdfEx.getMessage());
                    emailService.send(patient.getEmail(), subject, html);
                }
            } else {
                emailService.send(patient.getEmail(), subject, html);
            }

            log.info("Claim status notification sent to patient={} claim={} action={}",
                    patient.getId(), claim.getId(), action);
        } catch (Exception e) {
            log.warn("Claim status notification not sent for claim={}: {}", claim.getId(), e.getMessage());
        }
    }

    /**
     * FIX: "no batch claim submission" gap — ClaimsTab could only submit one
     * claim at a time. Per-claim results, not all-or-nothing: one claim
     * missing an ICD-10 code (submitClaim's own validation) shouldn't block
     * the rest of the batch.
     */
    @Transactional
    public BatchSubmitClaimsResponse batchSubmitClaims(TenantId tenantId, List<UUID> claimIds) {
        List<BatchSubmitResult> results = new ArrayList<>();
        int submitted = 0;
        int failed = 0;
        for (UUID claimId : claimIds) {
            try {
                submitClaim(tenantId, claimId, null);
                results.add(new BatchSubmitResult(claimId, true, "Submitted"));
                submitted++;
            } catch (Exception e) {
                results.add(new BatchSubmitResult(claimId, false, e.getMessage()));
                failed++;
            }
        }
        log.info("Batch claim submit: {} submitted, {} failed (of {} requested)",
                submitted, failed, claimIds.size());
        return new BatchSubmitClaimsResponse(submitted, failed, results);
    }

    // Backwards-compatible overload for callers that don't supply a scheme amount
    @Transactional
    public ClinicClaimResponse updateClaimStatus(TenantId tenantId, UUID claimId,
                                                 String action, String reason) {
        return updateClaimStatus(tenantId, claimId, action, reason, null);
    }

    // ── Outstanding / Payments / Revenue ──────────────────────────────────────
    // FIX: was three hardcoded stubs — getOutstanding derived a wrong,
    // incomplete answer (hardcoded "Patient" name, hardcoded totalPaid=0,
    // "balance" actually holding schemePortion, only SUBMITTED claims
    // counted); getPayments/getRevenue returned empty lists unconditionally.
    // clinic_payments already existed as a table with no entity pointed at
    // it — schema confirmed via \d before writing ClinicPayment, not
    // guessed from the DTOs.

    private static final DateTimeFormatter DAY_LABEL   = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    @Transactional
    public PaymentResponse recordPayment(TenantId tenantId, RecordPaymentRequest req, UUID recordedBy) {
        ClinicPayment payment = ClinicPayment.record(
                tenantId, null, req.patientId(), req.method(), req.amount(),
                req.reference(), req.notes(), recordedBy);
        paymentRepo.save(payment);
        log.info("Recorded payment patient={} amount={} method={}",
                req.patientId(), req.amount(), payment.getPaymentMethod());

        postPaymentJournal(tenantId, payment, req.bankAccountId());

        String patientName = patientRepo.findActiveById(tenantId, req.patientId())
                .map(ClinicPatient::getFullName).orElse("Patient");
        return new PaymentResponse(payment.getId(), payment.getPatientId(), patientName,
                payment.getPaymentMethod(), payment.getAmount(), payment.getReference(),
                payment.getRecordedAt(), payment.getNotes());
    }

    /**
     * FIX: backlog 1.6. Same "bankAccountId absent → log and skip,
     * never guess" treatment already applied to
     * invoicing.InvoicingAccountingEventHandler for the identical gap.
     */
    private void postPaymentJournal(TenantId tenantId, ClinicPayment payment, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Payment recorded for patient={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds. " +
                                "Payment was still recorded normally; this is a ledger-posting gap only.",
                        payment.getPatientId(), tenantId);
                return;
            }

            Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
            if (bankGl.isEmpty()) {
                log.warn("Bank account={} for tenant={} not found or not linked to a Chart of Accounts entry — " +
                        "payment for patient={} not posted", bankAccountId, tenantId, payment.getPatientId());
                return;
            }

            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("Chart of Accounts missing AR ({}) for tenant={} — payment for patient={} not posted",
                        AR_ACCOUNT_CODE, tenantId, payment.getPatientId());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankGl.get(), "Patient payment received", payment.getAmount(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Patient payment received", null, payment.getAmount()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Clinic payment received: " + payment.getId(),
                    payment.getReference(), "PAYMENT", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted payment journal for patient={} tenant={} amount={}",
                    payment.getPatientId(), tenantId, payment.getAmount());
        } catch (Exception e) {
            log.error("Failed to post payment journal for patient={} tenant={}: {}",
                    payment.getPatientId(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * FIX: previously only counted SUBMITTED claims, so a claim still in
     * DRAFT (patient may already owe for that visit) never appeared —
     * flagged explicitly in the audit. Now includes every non-REJECTED
     * claim, and computes real totalPaid from ClinicPayment instead of a
     * hardcoded zero.
     */
    @Transactional(readOnly = true)
    public List<OutstandingBalanceResponse> getOutstanding(TenantId tenantId) {
        List<ClinicClaim> claims = claimRepo.findAll(tenantId).stream()
                .filter(c -> !"REJECTED".equals(c.getStatus()) && !"VOIDED".equals(c.getStatus()))
                .toList();
        if (claims.isEmpty()) return List.of();

        Map<UUID, List<ClinicClaim>> byPatient = claims.stream()
                .collect(Collectors.groupingBy(ClinicClaim::getPatientId));

        Map<UUID, ClinicPatient> patients = patientRepo.findAllByIds(tenantId, byPatient.keySet()).stream()
                .collect(Collectors.toMap(ClinicPatient::getId, p -> p));

        Map<UUID, BigDecimal> paidByPatient = paymentRepo.findAllByTenant(tenantId).stream()
                .filter(p -> p.getPatientId() != null)
                .collect(Collectors.groupingBy(ClinicPayment::getPatientId,
                        Collectors.reducing(BigDecimal.ZERO, ClinicPayment::getAmount, BigDecimal::add)));

        return byPatient.entrySet().stream()
                .map(entry -> {
                    UUID patientId = entry.getKey();
                    List<ClinicClaim> patientClaims = entry.getValue();
                    BigDecimal totalBilled = patientClaims.stream()
                            .map(ClinicClaim::getPatientPortion).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalPaid = paidByPatient.getOrDefault(patientId, BigDecimal.ZERO);
                    BigDecimal balance = totalBilled.subtract(totalPaid).max(BigDecimal.ZERO);
                    Instant oldestUnpaid = patientClaims.stream()
                            .map(ClinicClaim::getCreatedAt).min(Instant::compareTo).orElse(null);
                    ClinicPatient p = patients.get(patientId);
                    return new OutstandingBalanceResponse(
                            patientId, p != null ? p.getFullName() : "Patient",
                            p != null ? p.getPhone() : null,
                            totalBilled, totalPaid, balance, oldestUnpaid, patientClaims.size());
                })
                .filter(r -> r.balance().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(OutstandingBalanceResponse::balance).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(TenantId tenantId, String period) {
        Instant[] range = resolvePeriodRange(period);
        List<ClinicPayment> payments = paymentRepo.findByPeriod(tenantId, range[0], range[1]);
        if (payments.isEmpty()) return List.of();

        Set<UUID> patientIds = payments.stream()
                .map(ClinicPayment::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));

        return payments.stream()
                .map(p -> new PaymentResponse(p.getId(), p.getPatientId(),
                        p.getPatientId() != null ? patientNames.getOrDefault(p.getPatientId(), "Patient") : "Patient",
                        p.getPaymentMethod(), p.getAmount(), p.getReference(), p.getRecordedAt(), p.getNotes()))
                .toList();
    }

    /**
     * One point per bucket (day, for "week"/"month"; month, for "year") so
     * the frontend can chart a trend, not just a single aggregate.
     * <p>
     * schemePaid is exact since the claim money ledger (V365): scheme payments by the time they were recorded.
     * Payments made before V365 appear once, as LEGACY, on the day the old claim was last updated. patientPaid is exact, sourced
     * directly from ClinicPayment.recordedAt.
     */
    @Transactional(readOnly = true)
    public List<RevenuePointResponse> getRevenue(TenantId tenantId, String period) {
        LocalDate today = LocalDate.now(SAST);
        record Bucket(String label, Instant from, Instant to) {}
        List<Bucket> buckets = new ArrayList<>();

        if ("year".equalsIgnoreCase(period)) {
            LocalDate start = today.withDayOfMonth(1).minusMonths(11);
            for (int i = 0; i < 12; i++) {
                LocalDate monthStart = start.plusMonths(i);
                buckets.add(new Bucket(monthStart.format(MONTH_LABEL),
                        monthStart.atStartOfDay(SAST).toInstant(),
                        monthStart.plusMonths(1).atStartOfDay(SAST).toInstant()));
            }
        } else {
            int days = "week".equalsIgnoreCase(period) ? 7 : 30; // "month" and any other default to a trailing 30-day window
            LocalDate start = today.minusDays(days - 1L);
            for (int i = 0; i < days; i++) {
                LocalDate day = start.plusDays(i);
                buckets.add(new Bucket(day.format(DAY_LABEL),
                        day.atStartOfDay(SAST).toInstant(), day.plusDays(1).atStartOfDay(SAST).toInstant()));
            }
        }

        List<ClinicClaim> allClaims = claimRepo.findAll(tenantId);
        List<ClinicPayment> allPayments = paymentRepo.findAllByTenant(tenantId);
        // What the scheme actually paid, from the ledger, by the day it was recorded.
        List<ClinicClaimLedgerService.PaymentAt> schemePayments = ledger.paymentsSince(tenantId.getValue(), buckets.get(0).from());

        return buckets.stream()
                .map(b -> {
                    List<ClinicClaim> claimsInBucket = allClaims.stream()
                            .filter(c -> !"VOIDED".equals(c.getStatus()))
                            .filter(c -> !c.getCreatedAt().isBefore(b.from()) && c.getCreatedAt().isBefore(b.to()))
                            .toList();
                    BigDecimal grossBilled = claimsInBucket.stream()
                            .map(ClinicClaim::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal schemePaid = schemePayments.stream()
                            .filter(p -> !p.at().isBefore(b.from()) && p.at().isBefore(b.to()))
                            .map(ClinicClaimLedgerService.PaymentAt::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal patientPaid = allPayments.stream()
                            .filter(p -> !p.getRecordedAt().isBefore(b.from()) && p.getRecordedAt().isBefore(b.to()))
                            .map(ClinicPayment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new RevenuePointResponse(b.label(), claimsInBucket.size(), grossBilled, schemePaid, patientPaid);
                })
                .toList();
    }

    private Instant[] resolvePeriodRange(String period) {
        LocalDate today = LocalDate.now(SAST);
        LocalDate from;
        if ("week".equalsIgnoreCase(period)) {
            from = today.minusDays(6);
        } else if ("year".equalsIgnoreCase(period)) {
            from = today.withDayOfYear(1);
        } else { // "month" and default
            from = today.withDayOfMonth(1);
        }
        return new Instant[]{
                from.atStartOfDay(SAST).toInstant(),
                today.plusDays(1).atStartOfDay(SAST).toInstant()
        };
    }

    // ── GL helper ─────────────────────────────────────────────────────────────

    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ClinicClaimResponse toResponse(ClinicClaim c, TenantId tenantId) {
        return toResponseWithNames(c, Map.of(), Map.of(), Map.of(c.getId(), ledger.totals(tenantId.getValue(), c.getId())));
    }

    private ClinicClaimResponse toResponseWithNames(ClinicClaim c,
                                                    Map<UUID, String> patientNames,
                                                    Map<UUID, String> practNames,
                                                    Map<UUID, ClaimLedgerRules.Totals> ledgerTotals) {
        ClaimLedgerRules.Totals t = ledgerTotals.getOrDefault(c.getId(), ClaimLedgerRules.Totals.NONE);
        List<ClinicClaimLineResponse> lines = c.getLines().stream()
                .map(l -> new ClinicClaimLineResponse(
                        l.getId(), l.getLineType(), l.getTariffCode(), l.getNappiCode(),
                        l.getIcd10Code(), l.getDescription(), l.getQuantity(),
                        l.getUnitPrice(), l.getGrossAmount(),
                        l.getSchemePortion(), l.getPatientPortion()))
                .toList();
        return new ClinicClaimResponse(
                c.getId(), c.getConsultationId(), c.getPatientId(),
                c.getPatientId() == null ? null : patientNames.get(c.getPatientId()),
                c.getPractitionerId(),
                c.getPractitionerId() == null ? null : practNames.get(c.getPractitionerId()),
                c.getStatus(), c.getSchemeName(), c.getMemberNumber(), c.getDependentCode(),
                c.getGrossAmount(), c.getSchemePortion(), c.getPatientPortion(),
                c.getSubmittedAt(), c.getReferenceNumber(), c.getRejectionReason(),
                lines, c.getCreatedAt(),
                t.paid(), t.writtenOff(), t.credited(), ClaimLedgerRules.outstanding(c.getSchemePortion(), t));
    }
}