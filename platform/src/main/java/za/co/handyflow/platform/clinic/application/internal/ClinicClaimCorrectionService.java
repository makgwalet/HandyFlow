package za.co.handyflow.platform.clinic.application.internal;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.dto.billing.ClinicClaimResponse;
import za.co.handyflow.platform.clinic.dto.billing.CreateClaimRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Correct and resubmit (CLINIC-DEC-003/004). A claim that has left DRAFT is never edited. To correct it, the old claim is
 * voided (which reverses its revenue journal and returns the consultation to the unbilled list) and a new DRAFT claim is
 * created for the same consultation, linked back to the one it replaces. Both steps happen in one transaction, so a failure
 * leaves the original untouched. Refused while a payment, write-off or credit note stands against the old claim.
 */
@Slf4j
@Service
public class ClinicClaimCorrectionService {

    private final ClinicClaimRepository claimRepo;
    private final ClinicClaimLedgerService ledger;
    private final ClinicClaimMoneyService money;
    private final ClinicBillingService billing;
    private final EntityManager em;

    public ClinicClaimCorrectionService(ClinicClaimRepository claimRepo, ClinicClaimLedgerService ledger,
                                        ClinicClaimMoneyService money, ClinicBillingService billing, EntityManager em) {
        this.claimRepo = claimRepo;
        this.ledger = ledger;
        this.money = money;
        this.billing = billing;
        this.em = em;
    }

    @Transactional
    public ClinicClaimResponse correct(TenantId tenant, UUID claimId, String rawReason, CreateClaimRequest corrected) {
        String reason = ClaimLedgerRules.reason(rawReason);
        ClinicClaim old = claimRepo.findActiveById(tenant, claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        ledger.lock(tenant.getValue(), List.of(claimId));
        ClaimLedgerRules.requireCorrectable(old.getStatus(), ledger.totals(tenant.getValue(), claimId));

        CreateClaimRequest request = corrected != null ? corrected : ClaimCorrectionRules.copyOf(old);
        UUID consultationId = old.getConsultationId();
        String note = ClaimCorrectionRules.correctionNote(old, reason);

        money.voidClaim(tenant, claimId, "Corrected: " + reason);
        em.flush(); // the void must reach the database before the new claim, or the one-live-claim-per-consultation index refuses it
        ClinicClaimResponse created = billing.createClaim(tenant, consultationId, request);

        ClinicClaim replacement = claimRepo.findActiveById(tenant, created.id()).orElseThrow(() -> new ResourceNotFoundException("Claim", created.id().toString()));
        replacement.correct(claimId, note);
        claimRepo.save(replacement);
        log.warn("CLAIM CORRECTED old={} new={}", claimId, replacement.getId());
        return billing.getClaimById(tenant, replacement.getId());
    }

    /** CLINIC-DEC-004: a corrected claim goes to the scheme again through the same checks as any submission. */
    @Transactional
    public ClinicClaimResponse resubmit(TenantId tenant, UUID claimId, String referenceNumber) {
        ClinicClaim claim = claimRepo.findActiveById(tenant, claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        if (claim.getCorrectsClaimId() == null) throw new IllegalStateException("Only a corrected claim is resubmitted here; use Submit for a new claim");
        if (!"DRAFT".equals(claim.getStatus())) throw new IllegalStateException("This corrected claim has already been submitted");
        return billing.submitClaim(tenant, claimId, referenceNumber);
    }
}
