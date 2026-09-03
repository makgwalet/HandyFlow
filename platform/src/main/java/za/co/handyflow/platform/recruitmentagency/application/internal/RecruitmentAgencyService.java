package za.co.handyflow.platform.recruitmentagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruitmentagency.domain.model.*;
import za.co.handyflow.platform.recruitmentagency.domain.repository.*;
import za.co.handyflow.platform.recruitmentagency.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Foundation layer only — practice profile and client portfolio CRUD.
 * Job requisitions, candidate pipeline, interviews, placement billing,
 * and client portal are separate, later services — see this module's
 * own package-info.java for the full planned layer list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentAgencyService {

    private final RecAgencyProfileRepository profileRepo;
    private final RecAgencyClientRepository clientRepo;
    private final RecAgencyRequisitionRepository requisitionRepo;
    private final RecAgencyCandidateRepository candidateRepo;
    private final RecAgencyPlacementRepository placementRepo;
    private final RecAgencyPlacementStageHistoryRepository stageHistoryRepo;
    private final za.co.handyflow.platform.shared.TenantSequenceService sequenceService;
    private final za.co.handyflow.platform.shared.FileStorageService fileStorageService;
    private final RecAgencyInvoiceRepository invoiceRepo;
    private final RecAgencyPaymentRepository paymentRepo;
    private final za.co.handyflow.platform.shared.EmailService emailService; // confirm not already present
    private final RecPortalAccessGrantRepository portalGrantRepo;
    private final za.co.handyflow.platform.evidence.application.EvidenceFacade evidenceFacade;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final AccountingFacade accountingFacade;
    // FIX (VAT sweep, module 2): replaces a hardcoded
    // subtotal.multiply(new BigDecimal("0.15")) fallback below for the
    // placement invoice's VAT calculation. NOTE: this module also has a
    // defaultFeePct() helper defaulting to "15.00" — that one is the
    // agency's placement-fee commission percentage, an unrelated
    // business concept that coincidentally shares the same numeral;
    // confirmed via its own doc comment and left untouched.
    private final VatRateProvider vatRateProvider;

    private static final String AR_ACCOUNT_CODE      = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final String VAT_ACCOUNT_CODE      = "2100";

    // ── Agency profile ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgencyProfileResponse getProfile(TenantId tenantId) {
        RecAgencyProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("AgencyProfile", tenantId.getValue().toString()));
        return toProfileResponse(profile);
    }

    @Transactional
    public AgencyProfileResponse upsertProfile(TenantId tenantId, UpdateAgencyProfileRequest req) {
        RecAgencyProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseGet(() -> RecAgencyProfile.create(tenantId.getValue(), req.agencyName()));
        profile.update(req.agencyName(), req.registrationNumber(), req.email(), req.phone(),
                req.physicalAddress(), req.logoUrl(), req.defaultPlacementFeePct());
        profileRepo.save(profile);
        log.info("Agency profile upserted tenant={} agencyName={}", tenantId.getValue(), req.agencyName());
        return toProfileResponse(profile);
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AgencyClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        BigDecimal agencyDefault = defaultFeePct(tenantId);
        return clientRepo.findAllActive(tenantId.getValue(), pageable)
                .map(c -> toClientResponse(c, agencyDefault));
    }

    @Transactional(readOnly = true)
    public AgencyClientResponse getClient(TenantId tenantId, UUID id) {
        return toClientResponse(findActiveClient(tenantId, id), defaultFeePct(tenantId));
    }

    @Transactional
    public AgencyClientResponse createClient(TenantId tenantId, CreateAgencyClientRequest req) {
        RecAgencyClient client = RecAgencyClient.create(tenantId.getValue(), req.tradingName(),
                req.registrationNumber(), req.industry(), req.placementFeePct(),
                req.guaranteePeriodDays(), req.contactName(), req.contactEmail(), req.contactPhone());
        clientRepo.save(client);
        log.info("Recruitment agency client created tenant={} client={}", tenantId.getValue(), req.tradingName());
        return toClientResponse(client, defaultFeePct(tenantId));
    }

    @Transactional
    public AgencyClientResponse updateClient(TenantId tenantId, UUID id, CreateAgencyClientRequest req) {
        RecAgencyClient client = findActiveClient(tenantId, id);
        client.update(req.tradingName(), req.industry(), req.placementFeePct(),
                req.guaranteePeriodDays(), req.contactName(), req.contactEmail(),
                req.contactPhone(), null);
        clientRepo.save(client);
        return toClientResponse(client, defaultFeePct(tenantId));
    }

    @Transactional
    public AgencyClientResponse deactivateClient(TenantId tenantId, UUID id) {
        RecAgencyClient client = findActiveClient(tenantId, id);
        if ("INACTIVE".equals(client.getStatus())) {
            throw new HandyFlowException("Client is already inactive", HttpStatus.BAD_REQUEST, "ALREADY_INACTIVE");
        }
        client.deactivate();
        clientRepo.save(client);
        log.info("Recruitment agency client deactivated tenant={} client={}", tenantId.getValue(), id);
        return toClientResponse(client, defaultFeePct(tenantId));
    }

    @Transactional
    public AgencyClientResponse reactivateClient(TenantId tenantId, UUID id) {
        RecAgencyClient client = findActiveClient(tenantId, id);
        client.reactivate();
        clientRepo.save(client);
        return toClientResponse(client, defaultFeePct(tenantId));
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        RecAgencyClient client = findActiveClient(tenantId, id);
        client.softDelete();
        clientRepo.save(client);
        log.info("Recruitment agency client deleted tenant={} client={}", tenantId.getValue(), id);
    }

    @Transactional
    public RequisitionResponse createRequisition(TenantId tenantId, CreateRequisitionRequest req) {
        RecAgencyClient client = findActiveClient(tenantId, req.clientId());
        String number = "REQ" + String.format("%04d",
                sequenceService.nextValue(tenantId, "RECRUITMENTAGENCY_REQUISITION"));
        RecAgencyRequisition requisition = RecAgencyRequisition.create(tenantId.getValue(), client.getId(),
                number, req.title(), req.description(), req.salaryMin(), req.salaryMax(),
                req.location(), req.employmentType(), req.targetStartDate());
        requisitionRepo.save(requisition);
        log.info("Requisition created tenant={} client={} title={}", tenantId.getValue(), client.getTradingName(), req.title());
        return toRequisitionResponse(requisition, client.getTradingName());
    }

    @Transactional(readOnly = true)
    public List<RequisitionResponse> getRequisitionsForClient(TenantId tenantId, UUID clientId) {
        RecAgencyClient client = findActiveClient(tenantId, clientId);
        return requisitionRepo.findByClient(clientId).stream()
                .map(r -> toRequisitionResponse(r, client.getTradingName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RequisitionResponse getRequisition(TenantId tenantId, UUID id) {
        RecAgencyRequisition r = findRequisition(tenantId, id);
        String clientName = clientRepo.findActiveById(tenantId.getValue(), r.getClientId())
                .map(RecAgencyClient::getTradingName).orElse("Unknown");
        return toRequisitionResponse(r, clientName);
    }

    @Transactional
    public RequisitionResponse cancelRequisition(TenantId tenantId, UUID id) {
        RecAgencyRequisition r = findRequisition(tenantId, id);
        r.cancel();
        requisitionRepo.save(r);
        String clientName = clientRepo.findActiveById(tenantId.getValue(), r.getClientId())
                .map(RecAgencyClient::getTradingName).orElse("Unknown");
        return toRequisitionResponse(r, clientName);
    }

    @Transactional
    public CandidateResponse createCandidate(TenantId tenantId, CreateCandidateRequest req) {
        RecAgencyCandidate candidate = RecAgencyCandidate.create(tenantId.getValue(), req.fullName(),
                req.email(), req.phone(), req.currentTitle(), req.currentEmployer(),
                req.skills(), req.source());
        candidateRepo.save(candidate);
        log.info("Candidate added tenant={} name={}", tenantId.getValue(), req.fullName());
        return toCandidateResponse(candidate);
    }

    @Transactional(readOnly = true)
    public Page<CandidateResponse> searchCandidates(TenantId tenantId, String search, Pageable pageable) {
        return candidateRepo.search(tenantId.getValue(), search, pageable).map(this::toCandidateResponse);
    }

    /**
     * Same upload pattern already proven in
     * payrollbureau.PayrollBureauService.uploadDocument() — ownership
     * check before the storage write, tenant-scoped storage path.
     */
    @Transactional
    public CandidateResponse uploadCv(TenantId tenantId, UUID candidateId,
                                      org.springframework.web.multipart.MultipartFile file,
                                      UUID uploadedBy, String uploadedByName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A file is required");
        }
        RecAgencyCandidate candidate = candidateRepo.findByTenantAndId(tenantId.getValue(), candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId.toString()));

        // NEW: Gate 0 — goes through EvidenceFacade now instead of
        // calling FileStorageService directly. Same hashing/size-limit/
        // storage behavior Expenses already proved end-to-end; this is
        // the second real consumer, not a reimplementation.
        za.co.handyflow.platform.evidence.dto.EvidenceResponse evidence = evidenceFacade.attach(
                tenantId, file, "CV", "recruitmentagency", "Candidate", candidateId,
               null, uploadedBy, uploadedByName);
        candidate.attachCvEvidence(evidence.id(), evidence.fileName());
        candidateRepo.save(candidate);
        log.info("CV uploaded candidate={} tenant={}", candidateId, tenantId.getValue());
        return toCandidateResponse(candidate);
    }

    @Transactional(readOnly = true)
    public byte[] downloadCv(TenantId tenantId, UUID candidateId) {
        RecAgencyCandidate candidate = candidateRepo.findByTenantAndId(tenantId.getValue(), candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId.toString()));

        // NEW: Evidence-backed CVs first — the path every new upload
        // takes. Falls back to the legacy direct-FileStorageService
        // path for candidates uploaded before this migration (real
        // data, e.g. Bongani Zulu, confirmed uploaded this session) —
        // same "legacy fallback, not a silent rewrite" shape already
        // established by RecruiterService.getCvBytes()'s own base64
        // fallback.
        if (candidate.getCvEvidenceId() != null) {
            return evidenceFacade.download(tenantId, candidate.getCvEvidenceId()).content();
        }

        if (candidate.getCvStorageKey() == null) {
            throw new ResourceNotFoundException("CV", candidateId.toString());
        }
        try {
            return fileStorageService.retrieve(candidate.getCvStorageKey());
        } catch (java.io.IOException e) {
            log.error("Failed to retrieve CV for candidate={}: {}", candidateId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve CV", e);
        }
    }

    @Transactional
    public PlacementResponse submitCandidate(TenantId tenantId, UUID requisitionId, SubmitCandidateRequest req) {
        RecAgencyRequisition requisition = findRequisition(tenantId, requisitionId);
        if (!"OPEN".equals(requisition.getStatus())) {
            throw new HandyFlowException("Can only submit candidates to an OPEN requisition — this one is "
                    + requisition.getStatus(), HttpStatus.BAD_REQUEST, "REQUISITION_NOT_OPEN");
        }
        RecAgencyCandidate candidate = candidateRepo.findByTenantAndId(tenantId.getValue(), req.candidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", req.candidateId().toString()));
        if (placementRepo.existsByRequisitionIdAndCandidateId(requisitionId, req.candidateId())) {
            throw new HandyFlowException("This candidate has already been submitted to this requisition",
                    HttpStatus.CONFLICT, "ALREADY_SUBMITTED");
        }

        RecAgencyPlacement placement = RecAgencyPlacement.create(tenantId.getValue(), requisitionId,
                req.candidateId(), requisition.getClientId());
        placementRepo.save(placement);
        stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                placement.getId(), null, "SUBMITTED", "Initial submission", null));

        log.info("Candidate={} submitted to requisition={} tenant={}",
                candidate.getFullName(), requisition.getRequisitionNumber(), tenantId.getValue());
        return toPlacementResponse(placement, requisition.getTitle(), candidate.getFullName());
    }

    @Transactional
    public PlacementResponse advanceStage(TenantId tenantId, UUID placementId, AdvanceStageRequest req, UUID changedBy) {
        RecAgencyPlacement placement = findPlacement(tenantId, placementId);
        String fromStage = placement.getStage();
        placement.moveToStage(req.toStage(), req.notes());
        placementRepo.save(placement);
        stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                placementId, fromStage, req.toStage(), req.notes(), changedBy));

        RecAgencyRequisition requisition = requisitionRepo.findById(placement.getRequisitionId()).orElseThrow();
        RecAgencyCandidate candidate = candidateRepo.findById(placement.getCandidateId()).orElseThrow();
        return toPlacementResponse(placement, requisition.getTitle(), candidate.getFullName());
    }

    /**
     * Marks PLACED — computes the placement fee from the offered salary
     * and the effective fee percentage (client override, or agency
     * default), and sets the guarantee-period end date from the
     * client's configured guaranteePeriodDays. Also marks the
     * requisition FILLED and the candidate PLACED, and closes out every
     * OTHER open placement for the same requisition as WITHDRAWN — once
     * one candidate is placed, the requisition is filled, and any other
     * candidates still in the pipeline for it are no longer live.
     */
    @Transactional
    public PlacementResponse markPlaced(TenantId tenantId, UUID placementId, MarkPlacedRequest req, UUID changedBy) {
        RecAgencyPlacement placement = findPlacement(tenantId, placementId);
        RecAgencyClient client = clientRepo.findActiveById(tenantId.getValue(), placement.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("AgencyClient", placement.getClientId().toString()));
        BigDecimal effectiveFeePct = client.getPlacementFeePct() != null
                ? client.getPlacementFeePct() : defaultFeePct(tenantId);

        String fromStage = placement.getStage();
        placement.markPlaced(req.offeredSalary(), effectiveFeePct, client.getGuaranteePeriodDays());
        placementRepo.save(placement);
        stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                placementId, fromStage, "PLACED",
                "Offered salary " + req.offeredSalary() + ", fee " + effectiveFeePct + "%", changedBy));

        RecAgencyRequisition requisition = requisitionRepo.findById(placement.getRequisitionId()).orElseThrow();
        requisition.markFilled();
        requisitionRepo.save(requisition);

        RecAgencyCandidate candidate = candidateRepo.findById(placement.getCandidateId()).orElseThrow();
        candidate.markPlaced();
        candidateRepo.save(candidate);

        // Withdraw every other still-open placement for this requisition
        for (RecAgencyPlacement other : placementRepo.findByRequisition(requisition.getId())) {
            if (!other.getId().equals(placementId) && !other.isTerminal()) {
                String otherFrom = other.getStage();
                other.moveToStage("WITHDRAWN", "Requisition filled by another candidate");
                placementRepo.save(other);
                stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                        other.getId(), otherFrom, "WITHDRAWN", "Requisition filled by another candidate", changedBy));
            }
        }

        log.info("Placement confirmed candidate={} requisition={} fee={} tenant={}",
                candidate.getFullName(), requisition.getRequisitionNumber(),
                placement.getPlacementFeeAmount(), tenantId.getValue());
        return toPlacementResponse(placement, requisition.getTitle(), candidate.getFullName());
    }

    @Transactional(readOnly = true)
    public List<PlacementResponse> getPlacementsForRequisition(TenantId tenantId, UUID requisitionId) {
        RecAgencyRequisition requisition = findRequisition(tenantId, requisitionId);
        return placementRepo.findByRequisition(requisitionId).stream()
                .map(p -> {
                    RecAgencyCandidate c = candidateRepo.findById(p.getCandidateId()).orElseThrow();
                    return toPlacementResponse(p, requisition.getTitle(), c.getFullName());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StageHistoryResponse> getStageHistory(TenantId tenantId, UUID placementId) {
        findPlacement(tenantId, placementId); // ownership check
        return stageHistoryRepo.findByPlacement(placementId).stream()
                .map(h -> new StageHistoryResponse(h.getId(), h.getFromStage(), h.getToStage(), h.getNotes(), h.getChangedAt()))
                .toList();
    }

    /**
     * Generates an invoice FROM a PLACED placement — requires PLACED,
     * not any earlier stage, same "don't bill ahead of the real event"
     * discipline as payrollbureau's generateFeeNote() requiring a
     * PROCESSED pay run. One invoice per placement, enforced by the
     * database's own unique constraint on placement_id, not just
     * checked here — but checked here too, for a clean 409 instead of
     * a raw constraint-violation exception reaching the client.
     */
    @Transactional
    public AgencyInvoiceResponse generateInvoice(TenantId tenantId, UUID placementId, CreateAgencyInvoiceRequest req) {
        RecAgencyPlacement placement = findPlacement(tenantId, placementId);
        if (!"PLACED".equals(placement.getStage())) {
            throw new HandyFlowException("Can only invoice a PLACED placement — this one is "
                    + placement.getStage(), HttpStatus.BAD_REQUEST, "NOT_PLACED");
        }
        if (invoiceRepo.existsByPlacementId(placementId)) {
            throw new HandyFlowException("This placement has already been invoiced",
                    HttpStatus.CONFLICT, "ALREADY_INVOICED");
        }

        RecAgencyRequisition requisition = requisitionRepo.findById(placement.getRequisitionId()).orElseThrow();
        RecAgencyCandidate candidate = candidateRepo.findById(placement.getCandidateId()).orElseThrow();

        BigDecimal subtotal = placement.getPlacementFeeAmount();
        BigDecimal vatAmount = req.includeVat()
                ? subtotal.multiply(vatRateProvider.rateFraction()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String invoiceNumber = "RAI" + String.format("%05d",
                sequenceService.nextValue(tenantId, "RECRUITMENTAGENCY_INVOICE:" + placement.getClientId()));

        String description = "Placement fee — " + candidate.getFullName() + " for " + requisition.getTitle();

        RecAgencyInvoice invoice = RecAgencyInvoice.create(tenantId.getValue(), placement.getClientId(),
                placementId, invoiceNumber, description, req.invoiceDate(), req.dueDate(), subtotal, vatAmount);
        invoiceRepo.save(invoice);

        postInvoiceRevenueJournal(tenantId, invoice, subtotal, vatAmount);

        log.info("Generated recruitment agency invoice={} client={} placement={} amount={}",
                invoiceNumber, placement.getClientId(), placementId, invoice.getTotal());
        return toInvoiceResponse(invoice);
    }

    @Transactional
    public AgencyInvoiceResponse sendInvoice(TenantId tenantId, UUID invoiceId) {
        RecAgencyInvoice invoice = invoiceRepo.findByTenantAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("AgencyInvoice", invoiceId.toString()));
        RecAgencyClient client = clientRepo.findActiveById(tenantId.getValue(), invoice.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("AgencyClient", invoice.getClientId().toString()));

        invoice.markSent();
        invoiceRepo.save(invoice);

        if (client.getContactEmail() != null) {
            // Reuses the same feeNote()-shaped email template already
            // established for the sibling Payroll Bureau module —
            // same "invoice number, amount, due date" shape, no reason
            // for a third near-identical template.
            emailService.send(client.getContactEmail(),
                    "Invoice " + invoice.getInvoiceNumber(),
                    za.co.handyflow.platform.shared.EmailTemplates.feeNote(
                            client.getTradingName(), invoice.getInvoiceNumber(),
                            invoice.getTotal().toPlainString(), invoice.getDueDate().toString()));
        }
        return toInvoiceResponse(invoice);
    }

    @Transactional
    public AgencyInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId,
                                               RecordAgencyPaymentRequest req, UUID userId, String userName) {
        RecAgencyInvoice invoice = invoiceRepo.findByTenantAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("AgencyInvoice", invoiceId.toString()));

        RecAgencyPayment payment = RecAgencyPayment.create(tenantId.getValue(), invoiceId, req.amount(),
                req.paidDate(), req.method(), req.reference(), userId, userName);
        paymentRepo.save(payment);

        invoice.recordPayment(req.amount());
        invoiceRepo.save(invoice);

        // FIX: backlog 1.6 — was previously nothing here.
        postPaymentJournal(tenantId, invoice, req.amount(), req.bankAccountId());

        log.info("Recorded payment={} against recruitment agency invoice={} newStatus={}",
                req.amount(), invoice.getInvoiceNumber(), invoice.getStatus());
        return toInvoiceResponse(invoice);
    }

    @Transactional(readOnly = true)
    public Page<AgencyInvoiceResponse> getInvoices(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActiveClient(tenantId, clientId); // ownership check
        return invoiceRepo.findByClient(clientId, pageable).map(this::toInvoiceResponse);
    }


    /**
     * Reports a guarantee-period failure — the candidate left before
     * the guarantee window elapsed. Transitions the placement to
     * FAILED_GUARANTEE and, if an invoice already exists for it, flags
     * that invoice as needing a credit note rather than generating one
     * automatically — actually producing a credit note (its own PDF,
     * its own accounting treatment) is real, separate work, not
     * something to fake with a status flag alone. If no invoice exists
     * yet (candidate left before billing caught up), nothing further
     * needs flagging — the placement staying FAILED_GUARANTEE is
     * sufficient to prevent generateInvoice() ever being called on it,
     * since that method requires PLACED, not FAILED_GUARANTEE.
     */
    @Transactional
    public PlacementResponse failGuarantee(TenantId tenantId, UUID placementId, String reason, UUID changedBy) {
        RecAgencyPlacement placement = findPlacement(tenantId, placementId);
        String fromStage = placement.getStage();
        placement.failGuarantee(reason);
        placementRepo.save(placement);
        stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                placementId, fromStage, "FAILED_GUARANTEE", reason, changedBy));

        invoiceRepo.findAll().stream() // see NOTE below on why this isn't a targeted query
                .filter(inv -> inv.getPlacementId().equals(placementId))
                .findFirst()
                .ifPresent(invoice -> {
                    invoice.flagForCreditNote("Guarantee period failure: " + reason);
                    invoiceRepo.save(invoice);
                    log.warn("[RecruitmentAgency] Invoice={} flagged for credit note — placement={} failed guarantee",
                            invoice.getInvoiceNumber(), placementId);
                });

        RecAgencyRequisition requisition = requisitionRepo.findById(placement.getRequisitionId()).orElseThrow();
        RecAgencyCandidate candidate = candidateRepo.findById(placement.getCandidateId()).orElseThrow();
        log.info("[RecruitmentAgency] Guarantee failure recorded placement={} candidate={} reason={}",
                placementId, candidate.getFullName(), reason);
        return toPlacementResponse(placement, requisition.getTitle(), candidate.getFullName());
    }

    @Transactional
    public PortalAccessGrantResponse invitePortalUser(TenantId tenantId, UUID clientId, String email, UUID invitedBy) {
        RecAgencyClient client = findActiveClient(tenantId, clientId);

        boolean alreadyGranted = portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException("This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        RecPortalAccessGrant grant = RecPortalAccessGrant.createInvite(tenantId.getValue(), clientId, email, invitedBy);
        portalGrantRepo.save(grant);

        emailService.send(email, client.getTradingName() + " has invited you to their recruitment portal",
                za.co.handyflow.platform.shared.EmailTemplates.portalInvite(
                        client.getTradingName(), "Recruitment Agency",
                        frontendUrl + "/recruitment-agency/portal/auth/accept-invite?token=" + grant.getInviteToken()));

        log.info("Recruitment agency portal invite sent: {} -> client={}", email, clientId);
        return toGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .map(this::toGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revokePortalAccess(TenantId tenantId, UUID clientId, UUID grantId, UUID revokedBy) {
        RecPortalAccessGrant grant = portalGrantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        portalGrantRepo.save(grant);
        return toGrantResponse(grant);
    }

    @Transactional
    public RequisitionResponse updateRequisition(TenantId tenantId, UUID id, UpdateRequisitionRequest req) {
        RecAgencyRequisition r = findRequisition(tenantId, id);
        r.update(req.title(), req.description(), req.salaryMin(), req.salaryMax(),
                req.location(), req.employmentType(), req.targetStartDate(), req.notes());
        requisitionRepo.save(r);
        String clientName = clientRepo.findActiveById(tenantId.getValue(), r.getClientId())
                .map(RecAgencyClient::getTradingName).orElse("Unknown");
        log.info("Requisition updated tenant={} requisition={}", tenantId.getValue(), r.getRequisitionNumber());
        return toRequisitionResponse(r, clientName);
    }

    @Transactional
    public RequisitionResponse reopenRequisition(TenantId tenantId, UUID id) {
        RecAgencyRequisition r = findRequisition(tenantId, id);
        r.reopen();
        requisitionRepo.save(r);
        String clientName = clientRepo.findActiveById(tenantId.getValue(), r.getClientId())
                .map(RecAgencyClient::getTradingName).orElse("Unknown");
        log.info("Requisition reopened tenant={} requisition={}", tenantId.getValue(), r.getRequisitionNumber());
        return toRequisitionResponse(r, clientName);
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecAgencyClient findActiveClient(TenantId tenantId, UUID id) {
        return clientRepo.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("AgencyClient", id.toString()));
    }

    private RecAgencyRequisition findRequisition(TenantId tenantId, UUID id) {
        return requisitionRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Requisition", id.toString()));
    }

    private RecAgencyPlacement findPlacement(TenantId tenantId, UUID id) {
        return placementRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Placement", id.toString()));
    }

    /**
     * Falls back to the standard 15.00% default if no profile has been
     * saved yet — matches RecAgencyProfile's own field default, so a
     * client created before the agency ever visits Settings still gets
     * a sane effectivePlacementFeePct instead of null.
     */
    private BigDecimal defaultFeePct(TenantId tenantId) {
        return profileRepo.findByTenantId(tenantId.getValue())
                .map(RecAgencyProfile::getDefaultPlacementFeePct)
                .orElse(new BigDecimal("15.00"));
    }

    private AgencyProfileResponse toProfileResponse(RecAgencyProfile p) {
        return new AgencyProfileResponse(p.getId(), p.getAgencyName(), p.getRegistrationNumber(),
                p.getEmail(), p.getPhone(), p.getPhysicalAddress(), p.getLogoUrl(),
                p.getDefaultPlacementFeePct());
    }

    private AgencyClientResponse toClientResponse(RecAgencyClient c, BigDecimal agencyDefault) {
        BigDecimal effective = c.getPlacementFeePct() != null ? c.getPlacementFeePct() : agencyDefault;
        return new AgencyClientResponse(c.getId(), c.getTradingName(), c.getRegistrationNumber(),
                c.getIndustry(), c.getPlacementFeePct(), effective, c.getGuaranteePeriodDays(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(),
                c.getOnboardedAt(), c.getStatus(), c.getNotes(), c.getCreatedAt());
    }

    private RequisitionResponse toRequisitionResponse(RecAgencyRequisition r, String clientName) {
        int candidateCount = placementRepo.findByRequisition(r.getId()).size();
        return new RequisitionResponse(r.getId(), r.getClientId(), clientName, r.getRequisitionNumber(),
                r.getTitle(), r.getDescription(), r.getSalaryMin(), r.getSalaryMax(), r.getLocation(),
                r.getEmploymentType(), r.getStatus(), r.getTargetStartDate(), r.getNotes(),
                candidateCount, r.getCreatedAt());
    }

    private CandidateResponse toCandidateResponse(RecAgencyCandidate c) {
        return new CandidateResponse(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getCurrentTitle(), c.getCurrentEmployer(), c.getSkills(), c.getSource(),
                c.getCvFileName(), c.getCvStorageKey() != null || c.getCvEvidenceId() != null,
                c.getNotes(), c.getStatus(), c.getCreatedAt());
    }

    private PlacementResponse toPlacementResponse(RecAgencyPlacement p, String requisitionTitle, String candidateName) {
        return new PlacementResponse(p.getId(), p.getRequisitionId(), requisitionTitle,
                p.getCandidateId(), candidateName, p.getClientId(), p.getStage(),
                p.getOfferedSalary(), p.getPlacementFeeAmount(), p.getPlacedAt(),
                p.getGuaranteeEndsAt(), p.getNotes(), p.getCreatedAt());
    }

    private AgencyInvoiceResponse toInvoiceResponse(RecAgencyInvoice inv) {
        return new AgencyInvoiceResponse(inv.getId(), inv.getInvoiceNumber(), inv.getDescription(),
                inv.getInvoiceDate(), inv.getDueDate(), inv.getSubtotal(), inv.getVatAmount(),
                inv.getTotal(), inv.getAmountPaid(), inv.balance(), inv.getStatus(),
                inv.getSentAt(), inv.getPaidAt(), inv.getPlacementId());
    }

    private PortalAccessGrantResponse toGrantResponse(RecPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }

    private void postInvoiceRevenueJournal(TenantId tenantId, RecAgencyInvoice invoice,
                                           BigDecimal subtotal, BigDecimal vatAmount) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, invoice.getId());
                return;
            }
            boolean hasVat = vatAmount != null && vatAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("Chart of Accounts missing VAT Output ({}) for tenant={} — invoice={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId, invoice.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Recruitment fee — " + invoice.getInvoiceNumber(), invoice.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Placement fee revenue — " + invoice.getInvoiceNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + invoice.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Recruitment agency invoice: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for recruitment agency invoice={} tenant={}",
                    invoice.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post revenue journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId, e.getMessage(), e);
        }
    }

    private void postPaymentJournal(TenantId tenantId, RecAgencyInvoice invoice,
                                    BigDecimal amount, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Payment recorded for invoice={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds.",
                        invoice.getInvoiceNumber(), tenantId);
                return;
            }
            Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
            if (bankGl.isEmpty()) {
                log.warn("Bank account={} for tenant={} not found or not linked — payment for invoice={} not posted",
                        bankAccountId, tenantId, invoice.getInvoiceNumber());
                return;
            }
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("Chart of Accounts missing AR ({}) for tenant={} — payment not posted", AR_ACCOUNT_CODE, tenantId);
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankGl.get(), "Payment received — " + invoice.getInvoiceNumber(), amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Payment received — " + invoice.getInvoiceNumber(), null, amount));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Payment received: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "PAYMENT", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted payment journal for invoice={} tenant={}", invoice.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post payment journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId, e.getMessage(), e);
        }
    }

    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

}