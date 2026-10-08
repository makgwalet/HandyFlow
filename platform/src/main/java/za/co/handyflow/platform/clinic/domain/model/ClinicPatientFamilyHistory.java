package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One condition in a patient's family: who had it, and when it started if known. */
@Entity
@Table(name = "clinic_patient_family_history")
@Getter
@NoArgsConstructor
public class ClinicPatientFamilyHistory {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID    tenantId;
    @Column(name = "patient_id")     UUID    patientId;
    String relative;
    @Column(name = "condition_name") String  conditionName;
    @Column(name = "age_at_onset")   Short   ageAtOnset;
    Boolean deceased;
    String notes;
    String status = "ACTIVE";
    @Column(name = "recorded_by")    UUID    recordedBy;
    @Column(name = "created_at")     Instant createdAt;
    @Column(name = "updated_at")     Instant updatedAt;

    public static ClinicPatientFamilyHistory create(TenantId t, UUID patientId, String relative, String conditionName,
                                                    Short ageAtOnset, Boolean deceased, String notes, UUID recordedBy) {
        var h = new ClinicPatientFamilyHistory();
        h.id = UUID.randomUUID(); h.tenantId = t.getValue(); h.patientId = patientId;
        h.relative = relative; h.conditionName = conditionName; h.ageAtOnset = ageAtOnset; h.deceased = deceased;
        h.notes = notes; h.recordedBy = recordedBy; h.createdAt = Instant.now(); h.updatedAt = h.createdAt;
        return h;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void update(String relative, String conditionName, Short ageAtOnset, Boolean deceased, String notes, String status) {
        this.relative = relative; this.conditionName = conditionName; this.ageAtOnset = ageAtOnset;
        this.deceased = deceased; this.notes = notes; if (status != null) this.status = status;
        this.updatedAt = Instant.now();
    }
}
