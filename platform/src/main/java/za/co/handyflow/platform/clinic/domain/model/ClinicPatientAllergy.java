package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_patient_allergies")
@Getter
@NoArgsConstructor
public class ClinicPatientAllergy {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID   tenantId;
    @Column(name = "patient_id")    UUID   patientId;
    String allergen;
    @Column(name = "allergen_type") String allergenType = "UNKNOWN";
    String reaction;
    String severity;
    String status = "ACTIVE";
    String notes;
    @Column(name = "recorded_by")   UUID    recordedBy;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;

    public static ClinicPatientAllergy create(TenantId tenantId, UUID patientId, String allergen,
                                              String allergenType, String reaction, String severity,
                                              String notes, UUID recordedBy) {
        ClinicPatientAllergy a = new ClinicPatientAllergy();
        a.id           = UUID.randomUUID();
        a.tenantId     = tenantId.getValue();
        a.patientId    = patientId;
        a.allergen     = allergen;
        a.allergenType = allergenType == null ? "UNKNOWN" : allergenType;
        a.reaction     = reaction;
        a.severity     = severity;
        a.notes        = notes;
        a.recordedBy   = recordedBy;
        a.createdAt    = Instant.now();
        a.updatedAt    = a.createdAt;
        return a;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    /** Null arguments leave the field unchanged. */
    public void update(String allergenType, String reaction, String severity, String status, String notes) {
        if (allergenType != null) this.allergenType = allergenType;
        if (reaction     != null) this.reaction     = reaction;
        if (severity     != null) this.severity     = severity;
        if (status       != null) this.status       = status;
        if (notes        != null) this.notes        = notes;
        this.updatedAt = Instant.now();
    }
}
