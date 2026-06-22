package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clinic_consultations")
@Getter
@NoArgsConstructor
public class ClinicConsultation {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID tenantId;
    @Column(name = "appointment_id")  UUID appointmentId;
    @Column(name = "patient_id")      UUID patientId;
    @Column(name = "practitioner_id") UUID practitionerId;
    @Column(name = "consulted_at")    Instant consultedAt;

    // Vitals
    @Column(name = "weight_kg")      BigDecimal weightKg;
    @Column(name = "height_cm")      BigDecimal heightCm;
    @Column(name = "blood_pressure") String     bloodPressure;
    @Column(name = "pulse_bpm")      Integer    pulseBpm;
    @Column(name = "temperature_c")  BigDecimal temperatureC;
    @Column(name = "oxygen_sat_pct") BigDecimal oxygenSatPct;

    // Clinical
    @Column(name = "chief_complaint") String chiefComplaint;
    String history;
    String examination;
    String diagnosis;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "icd10_codes", columnDefinition = "text[]")
    List<String> icd10Codes;

    @Column(name = "treatment_plan") String  treatmentPlan;
    @Column(name = "follow_up_days") Integer followUpDays;

    // Billing
    boolean billed = false;
    @Column(name = "billing_code")   String     billingCode;
    @Column(name = "billing_amount") BigDecimal billingAmount;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") UUID    deletedBy;
    @Version long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ClinicConsultation create(TenantId tenantId,
                                            UUID patientId, UUID appointmentId,
                                            UUID practitionerId, String chiefComplaint) {
        ClinicConsultation c = new ClinicConsultation();
        c.id             = UUID.randomUUID();
        c.tenantId       = tenantId.getValue();
        c.patientId      = patientId;
        c.appointmentId  = appointmentId;
        c.practitionerId = practitionerId;
        c.chiefComplaint = chiefComplaint;
        c.consultedAt    = Instant.now();
        c.billed         = false;
        c.createdAt      = Instant.now();
        c.updatedAt      = Instant.now();
        return c;
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void recordVitals(BigDecimal weightKg, BigDecimal heightCm,
                             String bloodPressure, Integer pulseBpm,
                             BigDecimal temperatureC, BigDecimal oxygenSatPct) {
        this.weightKg     = weightKg;
        this.heightCm     = heightCm;
        this.bloodPressure= bloodPressure;
        this.pulseBpm     = pulseBpm;
        this.temperatureC = temperatureC;
        this.oxygenSatPct = oxygenSatPct;
        this.updatedAt    = Instant.now();
    }

    public void recordClinical(String history, String examination, String diagnosis,
                               List<String> icd10Codes, String treatmentPlan,
                               Integer followUpDays) {
        this.history      = history;
        this.examination  = examination;
        this.diagnosis    = diagnosis;
        this.icd10Codes   = icd10Codes;
        this.treatmentPlan= treatmentPlan;
        this.followUpDays = followUpDays;
        this.updatedAt    = Instant.now();
    }

    public void markBilled(String billingCode, BigDecimal billingAmount) {
        this.billed        = true;
        this.billingCode   = billingCode;
        this.billingAmount = billingAmount;
        this.updatedAt     = Instant.now();
    }

    // Allows editing the chief complaint after creation (e.g. correction mid-consultation)
    public void updateChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
        this.updatedAt      = Instant.now();
    }
}
