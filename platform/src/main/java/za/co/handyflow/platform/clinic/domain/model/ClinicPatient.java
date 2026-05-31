package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clinic_patients")
@Getter
@NoArgsConstructor
public class ClinicPatient {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;

    @Column(name = "first_name") String firstName;
    @Column(name = "last_name")  String lastName;
    @Column(name = "id_number")  String idNumber;
    @Column(name = "date_of_birth") LocalDate dateOfBirth;
    String gender;
    String phone;
    String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    java.util.Map<String, String> address;

    @Column(name = "blood_type") String bloodType;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    List<String> allergies;

    @Column(name = "chronic_conditions", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    List<String> chronicConditions;

    @Column(name = "emergency_contact_name")  String emergencyContactName;
    @Column(name = "emergency_contact_phone") String emergencyContactPhone;

    String notes;
    boolean active = true;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") UUID deletedBy;
    @Version long version;

    public static ClinicPatient create(TenantId tenantId, String firstName,
                                       String lastName, String idNumber,
                                       LocalDate dateOfBirth, String gender,
                                       String phone, String email) {
        ClinicPatient p = new ClinicPatient();
        p.id         = UUID.randomUUID();
        p.tenantId   = tenantId.getValue();
        p.firstName  = firstName;
        p.lastName   = lastName;
        p.idNumber   = idNumber;
        p.dateOfBirth = dateOfBirth;
        p.gender     = gender;
        p.phone      = phone;
        p.email      = email;
        p.active     = true;
        p.createdAt  = Instant.now();
        p.updatedAt  = Instant.now();
        return p;
    }

    public void update(String phone, String email, String emergencyContactName,
                       String emergencyContactPhone, String bloodType,
                       List<String> allergies, List<String> chronicConditions,
                       String notes) {
        this.phone                = phone;
        this.email                = email;
        this.emergencyContactName  = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.bloodType            = bloodType;
        this.allergies            = allergies;
        this.chronicConditions    = chronicConditions;
        this.notes                = notes;
        this.updatedAt            = Instant.now();
    }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.active    = false;
        this.updatedAt = Instant.now();
    }
}