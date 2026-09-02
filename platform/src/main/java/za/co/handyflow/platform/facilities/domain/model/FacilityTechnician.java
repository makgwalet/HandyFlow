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
 * {@code HrEmployee} via {@code HrFacade} — this module declares no
 * dependency on {@code hr} (see package-info), matching {@code fleet}'s own
 * {@code Vehicle.assignedDriverId}, which is a bare UUID with no FK
 * integrity and no {@code hr} dependency in {@code fleet}'s own
 * {@code package-info.java} (confirmed by direct read before this module
 * was designed). {@code linkedUserId} is an optional, unvalidated
 * reference for tenants that want to tie a technician back to a system
 * user for their own reporting — never resolved through any facade.
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

    public void update(String name, String contactPhone, String contactEmail, String specialization) {
        if (name != null) this.name = name;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        if (specialization != null) this.specialization = specialization.toUpperCase();
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
