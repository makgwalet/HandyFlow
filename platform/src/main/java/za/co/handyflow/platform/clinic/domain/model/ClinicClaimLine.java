package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_claim_lines")
@Getter
@NoArgsConstructor
public class ClinicClaimLine {

    @Id UUID id;
    // WHY no @ManyToOne? ClinicClaim uses claimId UUID for the FK.
    // We keep it as plain UUID to avoid proxy loading — the parent already
    // eagerly loads lines via @OneToMany.
    @Column(name = "claim_id")        UUID claimId;
    @Column(name = "line_type")       String lineType;   // CONSULTATION/PROCEDURE/MEDICINE/CONSUMABLE
    @Column(name = "tariff_code")     String tariffCode;
    @Column(name = "nappi_code")      String nappiCode;
    @Column(name = "icd10_code")      String icd10Code;
    String description;
    BigDecimal quantity     = BigDecimal.ONE;
    @Column(name = "unit_price")      BigDecimal unitPrice;
    @Column(name = "gross_amount")    BigDecimal grossAmount;
    @Column(name = "scheme_portion")  BigDecimal schemePortion  = BigDecimal.ZERO;
    @Column(name = "patient_portion") BigDecimal patientPortion = BigDecimal.ZERO;
    @Column(name = "prescription_id") UUID prescriptionId;
    @Column(name = "sort_order")      int sortOrder = 0;
    @Column(name = "created_at")      Instant createdAt;

    public static ClinicClaimLine of(UUID claimId, String lineType,
                                     String tariffCode, String nappiCode,
                                     String icd10Code, String description,
                                     BigDecimal quantity, BigDecimal unitPrice,
                                     UUID prescriptionId, int sortOrder) {
        ClinicClaimLine l = new ClinicClaimLine();
        l.id             = UUID.randomUUID();
        l.claimId        = claimId;
        l.lineType       = lineType;
        l.tariffCode     = tariffCode;
        l.nappiCode      = nappiCode;
        l.icd10Code      = icd10Code;
        l.description    = description;
        l.quantity       = quantity;
        l.unitPrice      = unitPrice;
        l.grossAmount    = unitPrice.multiply(quantity);
        l.schemePortion  = BigDecimal.ZERO;   // set by billing rules / scheme tariff
        l.patientPortion = l.grossAmount;     // default: patient pays all until scheme confirms
        l.prescriptionId = prescriptionId;
        l.sortOrder      = sortOrder;
        l.createdAt      = Instant.now();
        return l;
    }

    public void applySchemePayment(BigDecimal schemePays) {
        this.schemePortion  = schemePays;
        this.patientPortion = this.grossAmount.subtract(schemePays);
    }
}
