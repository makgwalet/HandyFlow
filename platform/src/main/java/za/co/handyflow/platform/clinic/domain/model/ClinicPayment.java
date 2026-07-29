package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FIX: "broken payment endpoint" gap — BillingTab's "Record payment" modal
 * posted to POST /billing/payments, which didn't exist; getPayments()/
 * getRevenue() were hardcoded empty stubs. Maps to clinic_payments, an
 * existing table (confirmed via \d clinic_payments before writing this,
 * rather than guessing column names/types).
 * <p>
 * claimId is nullable — BillingTab's "Record payment" flow records against
 * a patient generally (patientId, method, amount, reference, notes), not
 * against a specific claim, matching the schema's own nullable FK.
 */
@Entity
@Table(name = "clinic_payments")
@Getter
@NoArgsConstructor
public class ClinicPayment {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID tenantId;
    @Column(name = "claim_id")   UUID claimId;
    @Column(name = "patient_id") UUID patientId;
    /** CASH | CARD | EFT | SCHEME_EFT | MEDICAL_AID — matches chk_payment_method. */
    @Column(name = "payment_method") String paymentMethod;
    BigDecimal amount;
    String reference;
    String notes;
    @Column(name = "recorded_by") UUID recordedBy;
    @Column(name = "recorded_at") Instant recordedAt;

    public static ClinicPayment record(TenantId tenantId, UUID claimId, UUID patientId,
                                       String paymentMethod, BigDecimal amount,
                                       String reference, String notes, UUID recordedBy) {
        ClinicPayment p = new ClinicPayment();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId.getValue();
        p.claimId = claimId;
        p.patientId = patientId;
        p.paymentMethod = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.toUpperCase() : "CASH";
        p.amount = amount;
        p.reference = reference;
        p.notes = notes;
        p.recordedBy = recordedBy;
        p.recordedAt = Instant.now();
        return p;
    }
}