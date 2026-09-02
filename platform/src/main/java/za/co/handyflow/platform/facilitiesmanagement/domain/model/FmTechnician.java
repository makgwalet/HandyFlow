package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** The FM company's own field staff — mirrors Module 5a's own FacilityTechnician shape. */
@Entity
@Table(name = "fm_technicians")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmTechnician {

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
    private String specialization = "GENERAL";

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

    public static FmTechnician create(TenantId tenantId, String name, String contactPhone,
                                       String contactEmail, String specialization) {
        FmTechnician t = new FmTechnician();
        t.tenantId = tenantId;
        t.name = name;
        t.contactPhone = contactPhone;
        t.contactEmail = contactEmail;
        t.specialization = specialization != null ? specialization.toUpperCase() : "GENERAL";
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
