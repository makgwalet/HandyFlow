package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A resolved person identity — deliberately thin. Holds only what's
 * needed to MATCH a person across modules (id number, email, phone, a
 * display name), never business data (no employee number, no PSiRA
 * number, no salary). Each module's own entity (HrEmployee,
 * SecurityGuard, RecApplicant, ...) stays the owner of its own fields;
 * this only provides a shared reference point. See
 * PersonIdentityService's Javadoc for the full reasoning and worked
 * example.
 */
@Entity
@Table(name = "person_identities")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PersonIdentity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "id_number")
    private String idNumber;

    private String email;
    private String phone;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PersonIdentity create(TenantId tenantId, String idNumber,
                                        String email, String phone, String fullName) {
        PersonIdentity p = new PersonIdentity();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId.getValue();
        p.idNumber = idNumber;
        p.email = email;
        p.phone = phone;
        p.fullName = fullName;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    /**
     * Fills in whichever contact fields were previously null — e.g. an
     * applicant resolved by name+email initially gets their id_number
     * added once they're hired and complete their HR profile. Never
     * overwrites an existing non-null value — the first module to capture
     * a fact about this person is treated as authoritative for that
     * field, avoiding one module's incomplete data silently clobbering
     * another's more complete record.
     */
    public void fillMissingFields(String idNumber, String email, String phone) {
        if (this.idNumber == null && idNumber != null) this.idNumber = idNumber;
        if (this.email == null && email != null) this.email = email;
        if (this.phone == null && phone != null) this.phone = phone;
        this.updatedAt = Instant.now();
    }
}