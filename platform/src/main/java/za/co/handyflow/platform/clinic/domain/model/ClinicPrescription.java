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
    @Column(name = "tenant_id")       UUID tenantId;
    @Column(name = "consultation_id") UUID consultationId;
    @Column(name = "patient_id")      UUID patientId;
    @Column(name = "practitioner_id") UUID practitionerId;
    @Column(name = "prescribed_at")   Instant prescribedAt;
    @Column(name = "medication_name") String medicationName;
    String dosage;
    String frequency;
    String duration;
    Integer quantity;
    int repeats = 0;
    String instructions;
    boolean dispensed = false;
    @Column(name = "dispensed_at") Instant dispensedAt;
    @Column(name = "created_at")   Instant createdAt;
    @Column(name = "updated_at")   Instant updatedAt;

    public static ClinicPrescription create(TenantId tenantId, UUID consultationId,
                                            UUID patientId, UUID practitionerId,
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

    public void dispense() {
        this.dispensed   = true;
        this.dispensedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }
}