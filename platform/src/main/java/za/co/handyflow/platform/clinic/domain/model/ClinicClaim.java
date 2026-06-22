package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clinic_claims")
@Getter
@NoArgsConstructor
public class ClinicClaim {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "consultation_id") UUID   consultationId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "practitioner_id") UUID   practitionerId;
    String status = "DRAFT";
    @Column(name = "scheme_name")     String schemeName;
    @Column(name = "member_number")   String memberNumber;
    @Column(name = "dependent_code")  String dependentCode;
    @Column(name = "gross_amount")    BigDecimal grossAmount    = BigDecimal.ZERO;
    @Column(name = "scheme_portion")  BigDecimal schemePortion  = BigDecimal.ZERO;
    @Column(name = "patient_portion") BigDecimal patientPortion = BigDecimal.ZERO;
    @Column(name = "submitted_at")    Instant submittedAt;
    @Column(name = "reference_number") String referenceNumber;
    @Column(name = "rejection_reason") String rejectionReason;
    String notes;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    @OneToMany(mappedBy = "claimId", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    List<ClinicClaimLine> lines = new ArrayList<>();

    public static ClinicClaim create(TenantId tenantId, UUID consultationId,
                                     UUID patientId, UUID practitionerId,
                                     String schemeName, String memberNumber,
                                     String dependentCode) {
        ClinicClaim c = new ClinicClaim();
        c.id             = UUID.randomUUID();
        c.tenantId       = tenantId.getValue();
        c.consultationId = consultationId;
        c.patientId      = patientId;
        c.practitionerId = practitionerId;
        c.status         = "DRAFT";
        c.schemeName     = schemeName;
        c.memberNumber   = memberNumber;
        c.dependentCode  = dependentCode;
        c.grossAmount    = BigDecimal.ZERO;
        c.schemePortion  = BigDecimal.ZERO;
        c.patientPortion = BigDecimal.ZERO;
        c.createdAt      = Instant.now();
        c.updatedAt      = Instant.now();
        return c;
    }

    public void addLine(ClinicClaimLine line) {
        lines.add(line);
        recalculate();
    }

    public void recalculate() {
        this.grossAmount    = lines.stream().map(ClinicClaimLine::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.schemePortion  = lines.stream().map(ClinicClaimLine::getSchemePortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.patientPortion = lines.stream().map(ClinicClaimLine::getPatientPortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.updatedAt      = Instant.now();
    }

    public void submit(String referenceNumber) {
        if (!"DRAFT".equals(this.status))
            throw new IllegalStateException("Only DRAFT claims can be submitted");
        this.status          = "SUBMITTED";
        this.referenceNumber = referenceNumber;
        this.submittedAt     = Instant.now();
        this.updatedAt       = Instant.now();
    }

    public void markAccepted() {
        this.status    = "ACCEPTED";
        this.updatedAt = Instant.now();
    }

    public void markRejected(String reason) {
        this.status          = "REJECTED";
        this.rejectionReason = reason;
        this.updatedAt       = Instant.now();
    }

    /**
     * FIX #5 — markPaid now takes the amount the scheme actually paid
     * and distributes it proportionally across lines via applySchemePayment().
     * Previously, lines always showed schemePortion=0, patientPortion=gross — wrong.
     *
     * schemePaid: total amount received from the medical aid.
     * If the scheme paid the full gross, patientPortion will be 0 on every line.
     * If null, defaults to the existing schemePortion total (no change to line splits).
     */
    public void markPaid(BigDecimal schemePaid) {
        this.status = "PAID";
        applyPaymentToLines(schemePaid);
        this.updatedAt = Instant.now();
    }

    /**
     * FIX #6 — markPartial records how much the scheme actually paid (not null).
     * Previously took no arguments — no way to record the partial amount.
     */
    public void markPartial(BigDecimal schemePaid) {
        this.status = "PARTIAL";
        applyPaymentToLines(schemePaid);
        this.updatedAt = Instant.now();
    }

    /**
     * Distributes a scheme payment proportionally across all claim lines.
     * Each line gets: line.gross / claim.gross * schemePaid.
     * Remainder assigned to patient portion.
     *
     * WHY proportional? Scheme may pay a flat rate per tariff code regardless
     * of actual billed amount. Proportional split is the correct default until
     * per-line scheme rates are available (requires EDI response parsing).
     */
    private void applyPaymentToLines(BigDecimal schemePaid) {
        if (schemePaid == null || this.grossAmount.compareTo(BigDecimal.ZERO) == 0) return;
        var totalScheme = schemePaid.min(this.grossAmount); // cap at gross
        for (ClinicClaimLine line : this.lines) {
            if (this.grossAmount.compareTo(BigDecimal.ZERO) > 0) {
                var lineScheme = totalScheme
                        .multiply(line.getGrossAmount())
                        .divide(this.grossAmount, 2, java.math.RoundingMode.HALF_UP);
                line.applySchemePayment(lineScheme);
            }
        }
        this.schemePortion  = lines.stream().map(ClinicClaimLine::getSchemePortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.patientPortion = this.grossAmount.subtract(this.schemePortion);
    }
}
