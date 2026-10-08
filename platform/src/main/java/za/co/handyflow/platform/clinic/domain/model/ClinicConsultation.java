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

    // Lifecycle: DRAFT -> SIGNED -> LOCKED, or DRAFT -> ABANDONED.
    // Nurse/doctor handoff (DEC-CLINIC-004) sits between DRAFT and SIGNED, see HANDOFF_TRANSITIONS.
    String status = "SIGNED";
    @Column(name = "reviewing_practitioner_id") UUID reviewingPractitionerId;
    @Column(name = "signed_at") Instant signedAt;

    // Billing
    boolean billed = false;
    @Column(name = "billing_code")   String     billingCode;
    @Column(name = "billing_amount") BigDecimal billingAmount;

    @Column(name = "created_by") UUID createdBy;
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

    /** Records who started this consultation (null when unknown). */
    public void startedBy(UUID userId) { this.createdBy = userId; }

    public static ClinicConsultation createDraft(TenantId tenantId,
                                                 UUID patientId, UUID appointmentId,
                                                 UUID practitionerId, String chiefComplaint) {
        ClinicConsultation c = create(tenantId, patientId, appointmentId, practitionerId, chiefComplaint);
        c.status = "DRAFT";
        return c;
    }

    /** Allowed handoff moves. DRAFT is the generic working state; NURSE_IN_PROGRESS marks nurse-led work. */
    public static final java.util.Map<String, java.util.Set<String>> HANDOFF_TRANSITIONS = java.util.Map.of(
            "DRAFT",              java.util.Set.of("NURSE_IN_PROGRESS", "READY_FOR_DOCTOR"),
            "NURSE_IN_PROGRESS",  java.util.Set.of("READY_FOR_DOCTOR"),
            "READY_FOR_DOCTOR",   java.util.Set.of("DOCTOR_REVIEWING", "RETURNED_TO_NURSE"),
            "DOCTOR_REVIEWING",   java.util.Set.of("DOCTOR_COMPLETED", "RETURNED_TO_NURSE"),
            "RETURNED_TO_NURSE",  java.util.Set.of("NURSE_IN_PROGRESS", "READY_FOR_DOCTOR"),
            "DOCTOR_COMPLETED",   java.util.Set.of("RETURNED_TO_NURSE"));

    /** The plain working copy (autosave target, shown in the drafts tray). */
    public boolean isDraft()  { return "DRAFT".equals(status); }
    public boolean isLocked() { return "LOCKED".equals(status) || "ABANDONED".equals(status); }
    public boolean isSigned() { return "SIGNED".equals(status); }
    /** Any not-yet-signed state, including the handoff states. */
    public boolean isUnsigned() { return HANDOFF_TRANSITIONS.containsKey(status); }
    /** Waiting on the other party: neither the nurse nor the doctor edits in these states. */
    public boolean isAwaitingHandoff() {
        return "READY_FOR_DOCTOR".equals(status) || "DOCTOR_COMPLETED".equals(status);
    }
    /** Signing is allowed from the plain draft or once the doctor has completed their review. */
    public boolean isSignable() { return isDraft() || "DOCTOR_COMPLETED".equals(status); }
    public boolean isAbandonable() {
        return isDraft() || "NURSE_IN_PROGRESS".equals(status) || "RETURNED_TO_NURSE".equals(status);
    }

    /** Moves along {@link #HANDOFF_TRANSITIONS}; anything else is rejected. */
    public void transitionTo(String next) {
        if (!HANDOFF_TRANSITIONS.getOrDefault(status, java.util.Set.of()).contains(next)) {
            throw new IllegalStateException("A consultation cannot move from " + status + " to " + next + ".");
        }
        this.status    = next;
        this.updatedAt = Instant.now();
    }

    public void assignReviewer(UUID practitionerId) {
        this.reviewingPractitionerId = practitionerId;
        this.updatedAt = Instant.now();
    }

    public void sign() {
        this.status    = "SIGNED";
        this.signedAt  = Instant.now();
        this.updatedAt = this.signedAt;
    }

    public void lock() {
        if (!"SIGNED".equals(status)) {
            throw new IllegalStateException("Only a SIGNED consultation can be locked (is " + status + ").");
        }
        this.status    = "LOCKED";
        this.updatedAt = Instant.now();
    }

    public void abandon() {
        this.status    = "ABANDONED";
        this.updatedAt = Instant.now();
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

    /** A voided claim puts the consultation back in the unbilled list (CLINIC-DEC-002: void, then a new claim). */
    public void markUnbilled() {
        this.billed        = false;
        this.billingCode   = null;
        this.billingAmount = null;
        this.updatedAt     = Instant.now();
    }

    // Allows editing the chief complaint after creation (e.g. correction mid-consultation)
    public void updateChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
        this.updatedAt      = Instant.now();
    }
}
