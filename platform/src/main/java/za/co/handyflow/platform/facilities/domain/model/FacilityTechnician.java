package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal maintenance staff member who can be assigned work orders.
 * Deliberately its own lightweight record rather than a link to
 * {@code HrEmployee} (no {@code hr} dependency, matching {@code fleet}).
 * <p>
 * {@code linkedUserId} ties the technician to a system user. As of Chunk 3
 * it drives access: a user holding only FACILITIES_TECHNICIAN sees and
 * works only the jobs assigned to their linked technician record. Chunk 3
 * fix: {@code update()} now sets it — previously it could be supplied on
 * create but never changed afterwards, so existing technicians could not be
 * linked.
 */
@Entity
@Table(name = "facility_technicians")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityTechnician {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(nullable = false)
    private String specialization = "GENERAL"; // ELECTRICAL, HVAC, PLUMBING, GENERAL, FIRE, OTHER

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FacilityTechnician create(TenantId tenantId, String name, String contactPhone,
                                            String contactEmail, String specialization, UUID linkedUserId) {
        FacilityTechnician t = new FacilityTechnician();
        t.tenantId = tenantId;
        t.name = name;
        t.contactPhone = contactPhone;
        t.contactEmail = contactEmail;
        t.specialization = specialization != null ? specialization.toUpperCase() : "GENERAL";
        t.linkedUserId = linkedUserId;
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    /** Full-replace semantics for the contact fields and the user link (null unlinks). */
    public void update(String name, String contactPhone, String contactEmail, String specialization, UUID linkedUserId) {
        if (name != null) this.name = name;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        if (specialization != null) this.specialization = specialization.toUpperCase();
        this.linkedUserId = linkedUserId;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}