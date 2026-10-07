package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_prescriptions")
@Getter
@NoArgsConstructor
public class ClinicPrescription {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "consultation_id") UUID   consultationId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "practitioner_id") UUID   practitionerId;
    @Column(name = "prescribed_at")   Instant prescribedAt;
    @Column(name = "medication_name") String medicationName;
    String dosage;
    String frequency;
    String duration;
    Integer quantity;
    int repeats = 0;
    String instructions;
    String nappiCode;
    Integer schedule;
    @Column(name = "allergy_override_reason") String allergyOverrideReason;
    @Column(name = "allergy_alert_summary")   String allergyAlertSummary;
    @Column(name = "fills_used") int fillsUsed = 0;
    boolean dispensed = false;
    @Column(name = "dispensed_at") Instant dispensedAt;
    @Column(name = "created_at")   Instant createdAt;
    @Column(name = "updated_at")   Instant updatedAt;

    public static ClinicPrescription create(TenantId tenantId,
                                            UUID consultationId, UUID patientId,
                                            UUID practitionerId,
                                            String medicationName, String dosage,
                                            String frequency, String duration,
                                            Integer quantity, int repeats,
                                            String instructions) {
        ClinicPrescription p = new ClinicPrescription();
        p.id             = UUID.randomUUID();
        p.tenantId       = tenantId.getValue();
        p.consultationId = consultationId;
        p.patientId      = patientId;
        p.practitionerId = practitionerId;
        p.prescribedAt   = Instant.now();
        p.medicationName = medicationName;
        p.dosage         = dosage;
        p.frequency      = frequency;
        p.duration       = duration;
        p.quantity       = quantity;
        p.repeats        = repeats;
        p.instructions   = instructions;
        p.dispensed      = false;
        p.createdAt      = Instant.now();
        p.updatedAt      = Instant.now();
        return p;
    }

    /** Overload that also records the NAPPI code and SA medicine schedule (0-8). */
    public static ClinicPrescription create(TenantId tenantId,
                                            UUID consultationId, UUID patientId,
                                            UUID practitionerId,
                                            String medicationName, String dosage,
                                            String frequency, String duration,
                                            Integer quantity, int repeats,
                                            String instructions,
                                            String nappiCode, Integer schedule) {
        ClinicPrescription p = create(tenantId, consultationId, patientId, practitionerId,
                medicationName, dosage, frequency, duration, quantity, repeats, instructions);
        p.nappiCode = (nappiCode == null || nappiCode.isBlank()) ? null : nappiCode.trim();
        p.schedule  = schedule;
        return p;
    }

    /** Keeps the prescriber's reason for prescribing despite a recorded-allergy name match. */
    public void recordAllergyOverride(String reason, String alertSummary) {
        this.allergyOverrideReason = reason;
        this.allergyAlertSummary   = alertSummary;
        this.updatedAt = Instant.now();
    }

    /** One original fill plus the authorised repeats. */
    public int totalFills() { return 1 + Math.max(0, repeats); }
    public int fillsRemaining() { return Math.max(0, totalFills() - fillsUsed); }

    /**
     * Records one fill and returns its number (1 = original). {@code dispensed} turns true only when the last
     * authorised fill is used, and {@code dispensedAt} is the time of the first fill.
     */
    public int recordFill() {
        if (fillsRemaining() == 0) {
            throw new IllegalStateException("All " + totalFills() + " authorised fills of this prescription have been used.");
        }
        fillsUsed++;
        Instant now = Instant.now();
        if (dispensedAt == null) dispensedAt = now;
        if (fillsRemaining() == 0) dispensed = true;
        updatedAt = now;
        return fillsUsed;
    }

    /** Marks the whole prescription as fully dispensed in one step (the old behaviour). */
    public void markDispensed() {
        while (fillsRemaining() > 0) recordFill();
    }
}
