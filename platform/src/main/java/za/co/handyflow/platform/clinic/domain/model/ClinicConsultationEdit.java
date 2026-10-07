package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of a consultation's clinical fields as they were BEFORE an edit
 * (table created in V84, previously never written). One row per edit of a
 * SIGNED consultation; DRAFT autosaves are not recorded.
 */
@Entity
@Table(name = "clinic_consultation_edits")
@Getter
@NoArgsConstructor
public class ClinicConsultationEdit {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID    tenantId;
    @Column(name = "consultation_id") UUID    consultationId;
    @Column(name = "edited_by")       UUID    editedBy;
    @Column(name = "edited_at")       Instant editedAt;

    @Column(name = "chief_complaint") String chiefComplaint;
    String history;
    String examination;
    String diagnosis;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "icd10_codes", columnDefinition = "text[]")
    List<String> icd10Codes;

    @Column(name = "treatment_plan")  String     treatmentPlan;
    @Column(name = "follow_up_days")  Integer    followUpDays;
    @Column(name = "weight_kg")       BigDecimal weightKg;
    @Column(name = "height_cm")       BigDecimal heightCm;
    @Column(name = "blood_pressure")  String     bloodPressure;
    @Column(name = "pulse_bpm")       Integer    pulseBpm;
    @Column(name = "temperature_c")   BigDecimal temperatureC;
    @Column(name = "oxygen_sat_pct")  BigDecimal oxygenSatPct;
    @Column(name = "change_reason")   String     changeReason;

    /** Captures the consultation's current (pre-edit) values. */
    public static ClinicConsultationEdit snapshotOf(ClinicConsultation c, UUID editedBy) {
        ClinicConsultationEdit e = new ClinicConsultationEdit();
        e.id             = UUID.randomUUID();
        e.tenantId       = c.getTenantId();
        e.consultationId = c.getId();
        e.editedBy       = editedBy;
        e.editedAt       = Instant.now();
        e.chiefComplaint = c.getChiefComplaint();
        e.history        = c.getHistory();
        e.examination    = c.getExamination();
        e.diagnosis      = c.getDiagnosis();
        e.icd10Codes     = c.getIcd10Codes() == null ? null : List.copyOf(c.getIcd10Codes());
        e.treatmentPlan  = c.getTreatmentPlan();
        e.followUpDays   = c.getFollowUpDays();
        e.weightKg       = c.getWeightKg();
        e.heightCm       = c.getHeightCm();
        e.bloodPressure  = c.getBloodPressure();
        e.pulseBpm       = c.getPulseBpm();
        e.temperatureC   = c.getTemperatureC();
        e.oxygenSatPct   = c.getOxygenSatPct();
        return e;
    }
}
