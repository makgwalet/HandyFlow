package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Extra profile details of a patient: identification, address, contact preferences, second emergency contact, payer. */
@Entity
@Table(name = "clinic_patient_profile")
@Getter
@NoArgsConstructor
public class ClinicPatientProfile {

    @Id @Column(name = "patient_id") UUID patientId;
    @Column(name = "tenant_id")  UUID tenantId;
    String title;
    @Column(name = "id_type")            String idType;
    String nationality;
    @Column(name = "preferred_language") String preferredLanguage;
    @Column(name = "preferred_contact")  String preferredContact;
    @Column(name = "address_line1")      String addressLine1;
    @Column(name = "address_line2")      String addressLine2;
    String suburb;
    String city;
    String province;
    @Column(name = "postal_code")        String postalCode;
    @Column(name = "emergency_relationship") String emergencyRelationship;
    @Column(name = "secondary_contact_name")         String secondaryContactName;
    @Column(name = "secondary_contact_phone")        String secondaryContactPhone;
    @Column(name = "secondary_contact_relationship") String secondaryContactRelationship;
    @Column(name = "payment_type")       String paymentType;
    @Column(name = "updated_by")         UUID    updatedBy;
    @Column(name = "updated_at")         Instant updatedAt;

    public static ClinicPatientProfile forPatient(TenantId t, UUID patientId) {
        var p = new ClinicPatientProfile();
        p.patientId = patientId; p.tenantId = t.getValue(); p.updatedAt = Instant.now();
        return p;
    }

    /** Replaces every detail: the form always sends the whole profile, so a cleared field is cleared. */
    public void replace(String title, String idType, String nationality, String preferredLanguage, String preferredContact,
                        String addressLine1, String addressLine2, String suburb, String city, String province, String postalCode,
                        String emergencyRelationship, String secondaryName, String secondaryPhone, String secondaryRelationship,
                        String paymentType, UUID by) {
        this.title = title; this.idType = idType; this.nationality = nationality; this.preferredLanguage = preferredLanguage;
        this.preferredContact = preferredContact; this.addressLine1 = addressLine1; this.addressLine2 = addressLine2;
        this.suburb = suburb; this.city = city; this.province = province; this.postalCode = postalCode;
        this.emergencyRelationship = emergencyRelationship; this.secondaryContactName = secondaryName;
        this.secondaryContactPhone = secondaryPhone; this.secondaryContactRelationship = secondaryRelationship;
        this.paymentType = paymentType; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}
