package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_practitioners")
@Getter
@NoArgsConstructor
public class ClinicPractitioner {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "first_name") String firstName;
    @Column(name = "last_name")  String lastName;
    String specialty;
    @Column(name = "hpcsa_number")    String hpcsaNumber;
    @Column(name = "practice_number") String practiceNumber;
    String phone;
    String email;
    boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") UUID deletedBy;
    @Version long version;

    public static ClinicPractitioner create(TenantId tenantId, String firstName,
                                            String lastName, String specialty,
                                            String hpcsaNumber, String practiceNumber,
                                            String phone, String email) {
        ClinicPractitioner p = new ClinicPractitioner();
        p.id             = UUID.randomUUID();
        p.tenantId       = tenantId.getValue();
        p.firstName      = firstName;
        p.lastName       = lastName;
        p.specialty      = specialty;
        p.hpcsaNumber    = hpcsaNumber;
        p.practiceNumber = practiceNumber;
        p.phone          = phone;
        p.email          = email;
        p.active         = true;
        p.createdAt      = Instant.now();
        p.updatedAt      = Instant.now();
        return p;
    }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.active    = false;
        this.updatedAt = Instant.now();
    }
}