package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Lifestyle and social history: one record per patient, replaced as a whole when edited. */
@Entity
@Table(name = "clinic_patient_social_history")
@Getter
@NoArgsConstructor
public class ClinicPatientSocialHistory {

    @Id @Column(name = "patient_id") UUID patientId;
    @Column(name = "tenant_id")         UUID    tenantId;
    @Column(name = "smoking_status")    String  smokingStatus = "UNKNOWN";
    @Column(name = "alcohol_use")       String  alcoholUse = "UNKNOWN";
    @Column(name = "substance_use")     String  substanceUse = "UNKNOWN";
    String occupation;
    @Column(name = "living_situation")  String  livingSituation;
    @Column(name = "physical_activity") String  physicalActivity;
    String notes;
    @Column(name = "updated_by")        UUID    updatedBy;
    @Column(name = "updated_at")         Instant updatedAt;

    public static ClinicPatientSocialHistory create(TenantId t, UUID patientId) {
        var s = new ClinicPatientSocialHistory();
        s.patientId = patientId; s.tenantId = t.getValue(); s.updatedAt = Instant.now();
        return s;
    }

    public void replace(String smoking, String alcohol, String substance, String occupation, String living,
                        String activity, String notes, UUID by) {
        this.smokingStatus = smoking; this.alcoholUse = alcohol; this.substanceUse = substance;
        this.occupation = occupation; this.livingSituation = living; this.physicalActivity = activity;
        this.notes = notes; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}
