package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.clinic.ClaimMoneyEvent;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Open;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Share;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;
import za.co.handyflow.platform.clinic.application.internal.ClinicClaimLedgerService.OpenClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scheme money on a claim (CLINIC-DEC-001 to 003, 006, 007): payments, write-offs, credit notes and voids are rows in an
 * append-only ledger, each checked by {@link ClaimLedgerRules}. The claim's own amounts never change. Every movement
 * publishes a {@link ClaimMoneyEvent}; Accounting owns what it posts. Voids and credit notes also reverse the revenue
 * journal this module itself posted when the claim was created.
 */
@Slf4j
@Service
public class ClinicClaimMoneyService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final String AR_ACCOUNT_CODE = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";

    private final ClinicClaimRepository claimRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicClaimLedgerService ledger;
    private final ApplicationEventPublisher events;
    private final AccountingFacade accountingFacade;

    public ClinicClaimMoneyService(ClinicClaimRepository claimRepo, ClinicConsultationRepository consultationRepo,
                                   ClinicClaimLedgerService ledger, ApplicationEventPublisher events, AccountingFacade accountingFacade) {
        this.claimRepo = claimRepo;
        this.consultationRepo = consultationRepo;
        this.ledger = ledger;
        this.events = events;
        this.accountingFacade = accountingFacade;
    }

    private static UUID user() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    private static UUID requireUser() {
        UUID u = user();
        if (u == null) throw new IllegalStateException("Sign in again: the person who authorises this must be known");
        return u;
    }

    private static LocalDate today() { return LocalDate.now(SAST); }

    private ClinicClaim lockedClaim(TenantId tenant, UUID claimId) {
        ledger.lock(tenant.getValue(), List.of(claimId));
        return claimRepo.findActiveById(tenant, claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
    }

    private void refreshStatus(TenantId tenant, ClinicClaim claim) {
        Totals t = ledger.totals(tenant.getValue(), claim.getId());
        String next = ClaimLedgerRules.statusAfter(claim.getStatus(), claim.getSchemePortion(), t);
        if (!next.equals(claim.getStatus())) claim.applyLedgerStatus(next);
        claimRepo.save(claim);
    }

    private void publish(TenantId tenant, String type, ClinicClaim claim, BigDecimal amount, String reference, UUID by) {
        events.publishEvent(ClaimMoneyEvent.of(tenant, type, claim.getId(), claim.getPatientId(), amount, reference, by));
    }

    // ── Scheme payments on one claim ─────────────────────────────────────────────────────────────────────────────────

    /** CLINIC-DEC-001: Mark paid receives the remaining balance. */
    @Transactional
    public ClinicClaim markPaid(TenantId tenant, UUID claimId, String reference) {
        ClinicClaim claim = lockedClaim(tenant, claimId);
        Totals t = ledger.totals(tenant.getValue(), claimId);
        BigDecimal amount = ClaimLedgerRules.markPaid(claim.getStatus(), ClaimLedgerRules.outstanding(claim.getSchemePortion(), t));
        return receive(tenant, claim, amount, reference);
    }

    @Transactional
    public ClinicClaim partial(TenantId tenant, UUID claimId, BigDecimal supplied, String reference) {
        ClinicClaim claim = lockedClaim(tenant, claimId);
        Totals t = ledger.totals(tenant.getValue(), claimId);
        BigDecimal amount = ClaimLedgerRules.partial(claim.getStatus(), ClaimLedgerRules.outstanding(claim.getSchemePortion(), t), supplied);
        return receive(tenant, claim, amount, reference);
    }

    private ClinicClaim receive(TenantId tenant, ClinicClaim claim, BigDecimal amount, String reference) {
        UUID by = user();
        ledger.addPayment(tenant.getValue(), claim.getId(), amount, today(), reference, null, "SINGLE", null, by);
        refreshStatus(tenant, claim);
        publish(tenant, ClaimMoneyEvent.SCHEME_PAYMENT_RECEIVED, claim, amount, reference, by);
        log.info("Scheme payment claim={} amount={} by={}", claim.getId(), amount, by);
        return claim;
    }

    // ── Controlled adjustments ───────────────────────────────────────────────────────────────────────────────────────

    /** CLINIC-DEC-001: a write-off is a separate, authorised transaction with a reason. */
    @Transactional
    public ClinicClaim writeOff(TenantId tenant, UUID claimId, BigDecimal supplied, String rawReason) {
        UUID by = requireUser();
        String reason = ClaimLedgerRules.reason(rawReason);
        ClinicClaim claim = lockedClaim(tenant, claimId);
        Totals t = ledger.totals(tenant.getValue(), claimId);
        BigDecimal amount = ClaimLedgerRules.writeOff(claim.getStatus(), ClaimLedgerRules.outstanding(claim.getSchemePortion(), t), supplied);
        ledger.addAdjustment(tenant.getValue(), claimId, "WRITE_OFF", amount, reason, by, today());
        refreshStatus(tenant, claim);
        publish(tenant, ClaimMoneyEvent.CLAIM_WRITTEN_OFF, claim, amount, null, by);
        log.warn("WRITE-OFF claim={} amount={} by={}", claimId, amount, by);
        return claim;
    }

    /** CLINIC-DEC-003: a credit note reduces what the scheme owes; it does not make the patient liable. */
    @Transactional
    public ClinicClaim creditNote(TenantId tenant, UUID claimId, BigDecimal supplied, String rawReason) {
        UUID by = requireUser();
        String reason = ClaimLedgerRules.reason(rawReason);
        ClinicClaim claim = lockedClaim(tenant, claimId);
        Totals t = ledger.totals(tenant.getValue(), claimId);
        BigDecimal amount = ClaimLedgerRules.creditNote(claim.getStatus(), ClaimLedgerRules.outstanding(claim.getSchemePortion(), t), supplied);
        String number = ledger.addAdjustment(tenant.getValue(), claimId, "CREDIT_NOTE", amount, reason, by, today());
        refreshStatus(tenant, claim);
        postReversal(tenant, claim, amount, "Credit note " + number + " on claim " + claim.getId());
        publish(tenant, ClaimMoneyEvent.CREDIT_NOTE_ISSUED, claim, amount, number, by);
        log.warn("CREDIT NOTE {} claim={} amount={} by={}", number, claimId, amount, by);
        return claim;
    }

    /** CLINIC-DEC-002: void only before any money has moved; the consultation goes back to the unbilled list. */
    @Transactional
    public ClinicClaim voidClaim(TenantId tenant, UUID claimId, String rawReason) {
        UUID by = requireUser();
        String reason = ClaimLedgerRules.reason(rawReason);
        ClinicClaim claim = lockedClaim(tenant, claimId);
        ClaimLedgerRules.requireVoidable(claim.getStatus(), ledger.totals(tenant.getValue(), claimId));
        claim.voidClaim(by, reason);
        claimRepo.save(claim);
        consultationRepo.findById(claim.getConsultationId()).ifPresent(c -> { c.markUnbilled(); consultationRepo.save(c); });
        postReversal(tenant, claim, claim.getGrossAmount(), "Claim voided: " + claim.getId());
        publish(tenant, ClaimMoneyEvent.CLAIM_VOIDED, claim, claim.getGrossAmount(), null, by);
        log.warn("CLAIM VOIDED claim={} by={}", claimId, by);
        return claim;
    }

    // ── CLINIC-DEC-006: one scheme payment across many claims ────────────────────────────────────────────────────────

    @Transactional
    public AllocationResponse allocate(TenantId tenant, AllocateRequest req) {
        if (req.schemeName() == null || req.schemeName().isBlank()) throw new IllegalArgumentException("Choose the medical aid scheme");
        List<OpenClaim> openClaims = ledger.openForScheme(tenant.getValue(), req.schemeName());
        Map<UUID, OpenClaim> byId = openClaims.stream().collect(Collectors.toMap(OpenClaim::id, Function.identity()));
        List<Open> open = openClaims.stream()
                .map(c -> new Open(c.id(), c.billedAt(), ClaimLedgerRules.outstanding(c.schemePortion(), c.totals())))
                .filter(o -> o.outstanding().signum() > 0).toList();

        boolean manual = req.shares() != null && !req.shares().isEmpty();
        List<Share> plan = manual
                ? ClaimLedgerRules.manual(req.amount(), open, req.shares().stream().map(s -> new Share(s.claimId(), s.amount())).toList(), req.overrideReason())
                : ClaimLedgerRules.oldestFirst(req.amount(), open);
        Map<UUID, BigDecimal> owedBefore = open.stream().collect(Collectors.toMap(Open::claimId, Open::outstanding));

        List<ShareResponse> shares = new ArrayList<>();
        for (Share s : plan) {
            OpenClaim c = byId.get(s.claimId());
            BigDecimal before = owedBefore.get(s.claimId());
            shares.add(new ShareResponse(s.claimId(), c.patientName(), c.reference(), before, s.amount(), before.subtract(s.amount())));
        }
        String method = manual ? "MANUAL" : "OLDEST_FIRST";
        if (req.preview()) return new AllocationResponse(false, method, null, req.amount(), shares);

        LocalDate received = req.receivedOn() == null ? today() : req.receivedOn();
        if (received.isAfter(today())) throw new IllegalArgumentException("The date received cannot be in the future");
        UUID by = user();
        UUID batch = UUID.randomUUID();
        // Lock first, then re-check against what the locked rows now say, so a payment recorded a moment ago is respected.
        ledger.lock(tenant.getValue(), plan.stream().map(Share::claimId).toList());
        for (Share s : plan) {
            ClinicClaim claim = claimRepo.findActiveById(tenant, s.claimId()).orElseThrow(() -> new ResourceNotFoundException("Claim", s.claimId().toString()));
            BigDecimal owed = ClaimLedgerRules.outstanding(claim.getSchemePortion(), ledger.totals(tenant.getValue(), claim.getId()));
            if (s.amount().compareTo(owed) > 0) throw new IllegalStateException("A claim's balance changed while you were allocating. Nothing was recorded; try again");
            ledger.addPayment(tenant.getValue(), claim.getId(), s.amount(), received, req.reference(), batch, method,
                    manual ? ClaimLedgerRules.reason(req.overrideReason()) : null, by);
            refreshStatus(tenant, claim);
            publish(tenant, ClaimMoneyEvent.SCHEME_PAYMENT_RECEIVED, claim, s.amount(), req.reference(), by);
        }
        log.info("Scheme payment allocated batch={} method={} amount={} claims={} by={}", batch, method, req.amount(), plan.size(), by);
        return new AllocationResponse(true, method, batch, req.amount(), shares);
    }

    // ── Reading ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Ledger ledgerOf(TenantId tenant, UUID claimId) {
        ClinicClaim claim = claimRepo.findActiveById(tenant, claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        Totals t = ledger.totals(tenant.getValue(), claimId);
        return new Ledger(claimId, claim.getStatus(), claim.getSchemePortion(), t.paid(), t.writtenOff(), t.credited(),
                ClaimLedgerRules.outstanding(claim.getSchemePortion(), t), ledger.entries(tenant.getValue(), claimId));
    }

    // ── Reversing what this module posted ────────────────────────────────────────────────────────────────────────────

    /** Credit AR, debit Revenue: the opposite of the entry written when the claim was created. Never lets a posting failure undo the claim. */
    private void postReversal(TenantId tenant, ClinicClaim claim, BigDecimal amount, String memo) {
        try {
            UUID ar = accountId(tenant, AR_ACCOUNT_CODE), revenue = accountId(tenant, REVENUE_ACCOUNT_CODE);
            if (ar == null || revenue == null) {
                log.warn("Chart of Accounts missing {} or {} for tenant={}; reversal for claim={} not posted", AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenant, claim.getId());
                return;
            }
            var lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(revenue, memo, amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(ar, memo, null, amount));
            var created = accountingFacade.createJournalEntry(tenant,
                    new CreateJournalEntryRequest(today(), memo, claim.getId().toString(), "MANUAL", lines));
            accountingFacade.postJournalEntry(tenant, created.id());
        } catch (Exception e) {
            log.error("Failed to post reversal for claim={} tenant={}: {}", claim.getId(), tenant, e.getMessage(), e);
        }
    }

    private UUID accountId(TenantId tenant, String code) {
        return accountingFacade.getAccounts(tenant).stream().filter(a -> code.equals(a.accountCode())).map(a -> a.id()).findFirst().orElse(null);
    }
}
