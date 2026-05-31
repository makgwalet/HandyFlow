// security/domain/model/Guard.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_guards")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Guard {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "psira_number")
    private String psiraNumber;

    @Column(name = "id_number")
    private String idNumber;

    private String phone;

    @Column(name = "photo_url")
    private String photoUrl;

    private String grade;

    @Column(nullable = false)
    private boolean active = true;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    // In Guard.java — add photo domain method:
    public void updatePhoto(String photoUrl) {
        this.photoUrl = photoUrl;
        this.updatedAt = Instant.now();
    }

    @Version
    private Long version;

    public static Guard create(TenantId tenantId, String firstName, String lastName,
                               String psiraNumber, String idNumber, String phone,
                               String grade) {
        Guard g = new Guard();
        g.tenantId    = tenantId;
        g.firstName   = firstName.trim();
        g.lastName    = lastName.trim();
        g.psiraNumber = psiraNumber;
        g.idNumber    = idNumber;
        g.phone       = phone;
        g.grade       = grade;
        g.active      = true;
        g.createdAt   = Instant.now();
        g.updatedAt   = Instant.now();
        return g;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void update(String firstName, String lastName, String psiraNumber,
                       String idNumber, String phone, String grade, String notes) {
        this.firstName   = firstName.trim();
        this.lastName    = lastName.trim();
        this.psiraNumber = psiraNumber;
        this.idNumber    = idNumber;
        this.phone       = phone;
        this.grade       = grade;
        this.notes       = notes;
        this.updatedAt   = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}