package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One measured value (BP, weight, glucose...). The same rows feed consultations, trends and charts. */
@Entity
@Table(name = "clinic_observations")
@Getter
@NoArgsConstructor
public class ClinicObservation {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "consultation_id") UUID   consultationId;
    String code;
    @Column(name = "value_numeric")   BigDecimal valueNumeric;
    String unit;
    @Column(name = "ref_low")         BigDecimal refLow;
    @Column(name = "ref_high")        BigDecimal refHigh;
    @Column(name = "abnormal_flag")   String abnormalFlag;
    @Column(name = "taken_at")        Instant takenAt;
    @Column(name = "taken_by")        UUID   takenBy;
    String source = "MANUAL";
    String status = "FINAL";
    String notes;
    @Column(name = "created_at")      Instant createdAt;

    public static ClinicObservation of(TenantId tenantId, UUID patientId, UUID consultationId,
                                       ObservationCode code, BigDecimal value,
                                       BigDecimal refLow, BigDecimal refHigh,
                                       Instant takenAt, UUID takenBy, String source, String notes) {
        ClinicObservation o = new ClinicObservation();
        o.id             = UUID.randomUUID();
        o.tenantId       = tenantId.getValue();
        o.patientId      = patientId;
        o.consultationId = consultationId;
        o.code           = code.name();
        o.valueNumeric   = value;
        o.unit           = code.unit();
        o.refLow         = refLow;
        o.refHigh        = refHigh;
        o.abnormalFlag   = flag(value, refLow, refHigh);
        o.takenAt        = takenAt != null ? takenAt : Instant.now();
        o.takenBy        = takenBy;
        o.source         = source == null ? "MANUAL" : source;
        o.notes          = notes;
        o.createdAt      = Instant.now();
        return o;
    }

    /** LOW/NORMAL/HIGH only when the caller supplied a range; otherwise no judgement is made. */
    static String flag(BigDecimal v, BigDecimal low, BigDecimal high) {
        if (low == null && high == null) return null;
        if (low != null && v.compareTo(low) < 0) return "LOW";
        if (high != null && v.compareTo(high) > 0) return "HIGH";
        return "NORMAL";
    }

    public void markEnteredInError() { this.status = "ENTERED_IN_ERROR"; }
}
