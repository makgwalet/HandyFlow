package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "clinic_patient_conditions")
@Getter
@NoArgsConstructor
public class ClinicPatientCondition {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID   tenantId;
    @Column(name = "patient_id")     UUID   patientId;
    @Column(name = "condition_name") String conditionName;
    @Column(name = "icd10_code")     String icd10Code;
    String status = "ACTIVE";
    @Column(name = "onset_date")     LocalDate onsetDate;
    String notes;
    @Column(name = "recorded_by")    UUID    recordedBy;
    @Column(name = "created_at")     Instant createdAt;
    @Column(name = "updated_at")     Instant updatedAt;

    public static ClinicPatientCondition create(TenantId tenantId, UUID patientId, String conditionName,
                                                String icd10Code, LocalDate onsetDate, String notes,
                                                UUID recordedBy) {
        ClinicPatientCondition c = new ClinicPatientCondition();
        c.id            = UUID.randomUUID();
        c.tenantId      = tenantId.getValue();
        c.patientId     = patientId;
        c.conditionName = conditionName;
        c.icd10Code     = icd10Code;
        c.onsetDate     = onsetDate;
        c.notes         = notes;
        c.recordedBy    = recordedBy;
        c.createdAt     = Instant.now();
        c.updatedAt     = c.createdAt;
        return c;
    }

    /** Active or controlled conditions are shown as the patient's current conditions. */
    public boolean isCurrent() { return "ACTIVE".equals(status) || "CONTROLLED".equals(status); }

    public void update(String icd10Code, LocalDate onsetDate, String status, String notes) {
        if (icd10Code != null) this.icd10Code = icd10Code;
        if (onsetDate != null) this.onsetDate = onsetDate;
        if (status    != null) this.status    = status;
        if (notes     != null) this.notes     = notes;
        this.updatedAt = Instant.now();
    }
}
