package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.AccountDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/** The patient account tab: one row per visit with its claim, the payments received and the balance owing (patch 0161). */
@Service
@RequiredArgsConstructor
public class ClinicAccountService {

    private final ClinicPatientRepository      patientRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicClaimRepository        claimRepo;
    private final ClinicPaymentRepository      paymentRepo;
    private final ClinicClaimLedgerService     ledger;

    @Transactional(readOnly = true)
    public AccountResponse account(TenantId t, UUID patientId) {
        patientRepo.findActiveById(t, patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        List<AccountPayment> payments = paymentRepo.findByPatient(t, patientId).stream()
                .map(p -> new AccountPayment(p.getId(), p.getClaimId(), p.getRecordedAt(), p.getPaymentMethod(), p.getAmount(), p.getReference()))
                .sorted(Comparator.comparing(AccountPayment::paidAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<UUID, ClinicClaim> byVisit = new HashMap<>();
        for (ClinicClaim c : claimRepo.findByPatient(t, patientId)) byVisit.put(c.getConsultationId(), c);
        Map<UUID, ClaimLedgerRules.Totals> totals = ledger.totalsByClaim(t.getValue());

        List<AccountVisit> visits = new ArrayList<>();
        for (ClinicConsultation c : consultationRepo.findByPatient(t, patientId)) {
            ClinicClaim claim = byVisit.get(c.getId());
            if (claim == null) {
                visits.add(new AccountVisit(c.getId(), null, c.getConsultedAt(), c.getChiefComplaint(), AccountRules.NOT_BILLED,
                        null, c.getBillingAmount(), null, null, null, java.math.BigDecimal.ZERO));
                continue;
            }
            ClaimLedgerRules.Totals lt = totals.getOrDefault(claim.getId(), ClaimLedgerRules.Totals.NONE);
            visits.add(new AccountVisit(c.getId(), claim.getId(), c.getConsultedAt(), c.getChiefComplaint(), claim.getStatus(),
                    claim.getSchemeName(), claim.getGrossAmount(), claim.getSchemePortion(), claim.getPatientPortion(),
                    ClaimLedgerRules.outstanding(claim.getSchemePortion(), lt), AccountRules.paidAgainst(claim.getId(), payments)));
        }
        visits.sort(Comparator.comparing(AccountVisit::visitDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return AccountRules.build(patientId, visits, payments);
    }
}
