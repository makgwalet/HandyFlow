package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a patient's medical aid / scheme details.
 * One patient can have multiple records (plan changes over time),
 * but typically only one is active.
 *
 * WHY store these here vs. on ClinicPatient?
 * A patient can change schemes, have multiple plans (e.g. main + gap cover),
 * and a principal's record applies to all dependants — normalising it here
 * avoids duplicating scheme details on every dependant's patient record.
 */
@Entity
@Table(name = "clinic_medical_aids")
@Getter
@NoArgsConstructor
public class ClinicMedicalAid {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID    tenantId;
    @Column(name = "patient_id") UUID    patientId;
    @Column(name = "scheme_name")   String schemeName;
    @Column(name = "plan_name")     String planName;
    @Column(name = "member_number") String memberNumber;
    @Column(name = "dependent_code") String dependentCode;
    @Column(name = "principal_member") String principalMember;
    @Column(name = "scheme_contact_phone") String schemeContactPhone;
    @Setter boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static ClinicMedicalAid create(UUID tenantId, UUID patientId,
                                          String schemeName, String planName,
                                          String memberNumber, String dependentCode,
                                          String principalMember) {
        var m = new ClinicMedicalAid();
        m.id              = UUID.randomUUID();
        m.tenantId        = tenantId;
        m.patientId       = patientId;
        m.schemeName      = schemeName;
        m.planName        = planName;
        m.memberNumber    = memberNumber;
        m.dependentCode   = dependentCode;
        m.principalMember = principalMember;
        m.active          = true;
        m.createdAt       = Instant.now();
        m.updatedAt       = Instant.now();
        return m;
    }
}
