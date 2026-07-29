package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPayment;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPaymentRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * FIX: "no patient statement of account" gap — nothing aggregated a
 * patient's full billing history across multiple claims/visits into one
 * document. Deliberately shows claims and payments as two separate
 * sections rather than trying to reconcile which payment settled which
 * claim — ClinicPayment.claimId is nullable (BillingTab's "Record payment"
 * flow records against a patient generally, not a specific claim), so a
 * per-claim balance would require guessing an allocation that isn't
 * actually recorded anywhere. Total patient portion owed minus total
 * payments received is the number that's actually backed by real data.
 */
@Service
@RequiredArgsConstructor
public class ClinicStatementOfAccountService {

    private final ClinicPatientRepository patientRepo;
    private final ClinicClaimRepository claimRepo;
    private final ClinicPaymentRepository paymentRepo;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    public record ClaimLine(
            String schemeName, LocalDate date, BigDecimal grossAmount,
            BigDecimal schemePortion, BigDecimal patientPortion, String status
    ) {}

    public record PaymentLine(LocalDate date, String method, BigDecimal amount, String reference) {}

    public record PatientStatement(
            String patientName, String patientPhone, String patientEmail,
            LocalDate periodFrom, LocalDate periodTo,
            List<ClaimLine> claims, List<PaymentLine> payments,
            BigDecimal totalPatientPortion, BigDecimal totalPaid, BigDecimal balance
    ) {}

    @Transactional(readOnly = true)
    public PatientStatement buildStatement(TenantId tenantId, UUID patientId, LocalDate from, LocalDate to) {
        ClinicPatient patient = patientRepo.findActiveById(tenantId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        List<ClinicClaim> claims = claimRepo.findByPatient(tenantId, patientId).stream()
                .filter(c -> !"DRAFT".equals(c.getStatus()))
                .filter(c -> inRange(instantToDate(c.getCreatedAt()), from, to))
                .toList();

        List<ClinicPayment> payments = paymentRepo.findByPatient(tenantId, patientId).stream()
                .filter(p -> inRange(instantToDate(p.getRecordedAt()), from, to))
                .toList();

        List<ClaimLine> claimLines = claims.stream()
                .map(c -> new ClaimLine(
                        c.getSchemeName(), instantToDate(c.getCreatedAt()),
                        c.getGrossAmount(), c.getSchemePortion(), c.getPatientPortion(), c.getStatus()))
                .toList();

        List<PaymentLine> paymentLines = payments.stream()
                .map(p -> new PaymentLine(instantToDate(p.getRecordedAt()), p.getPaymentMethod(), p.getAmount(), p.getReference()))
                .toList();

        BigDecimal totalPatientPortion = claims.stream()
                .filter(c -> !"REJECTED".equals(c.getStatus()))
                .map(ClinicClaim::getPatientPortion).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = payments.stream().map(ClinicPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalPatientPortion.subtract(totalPaid).max(BigDecimal.ZERO);

        return new PatientStatement(
                patient.getFullName(), patient.getPhone(), patient.getEmail(),
                from, to, claimLines, paymentLines,
                totalPatientPortion, totalPaid, balance);
    }

    private LocalDate instantToDate(Instant instant) {
        return instant != null ? instant.atZone(SAST).toLocalDate() : null;
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) return true;
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}