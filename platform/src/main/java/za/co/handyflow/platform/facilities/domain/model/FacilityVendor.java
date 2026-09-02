package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An external contractor/supplier used for work the internal technician
 * pool can't or shouldn't do itself (specialist HVAC servicing, elevator
 * certification, electrical compliance testing, etc.).
 */
@Entity
@Table(name = "facility_vendors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityVendor {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "service_type", nullable = false)
    private String serviceType; // ELECTRICAL, HVAC, PLUMBING, FIRE, ELEVATOR, GENERAL, OTHER

    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_phone")
    private String contactPhone;
    @Column(name = "contact_email")
    private String contactEmail;

    private String notes;

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

    public static FacilityVendor create(TenantId tenantId, String companyName, String serviceType,
                                         String contactName, String contactPhone, String contactEmail,
                                         String notes) {
        FacilityVendor v = new FacilityVendor();
        v.tenantId = tenantId;
        v.companyName = companyName;
        v.serviceType = serviceType != null ? serviceType.toUpperCase() : "GENERAL";
        v.contactName = contactName;
        v.contactPhone = contactPhone;
        v.contactEmail = contactEmail;
        v.notes = notes;
        v.createdAt = Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }

    public void update(String companyName, String serviceType, String contactName,
                        String contactPhone, String contactEmail, String notes) {
        if (companyName != null) this.companyName = companyName;
        if (serviceType != null) this.serviceType = serviceType.toUpperCase();
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.notes = notes;
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
