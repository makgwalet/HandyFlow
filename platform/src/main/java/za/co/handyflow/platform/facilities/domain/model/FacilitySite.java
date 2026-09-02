package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A physical premises the tenant occupies (owned or leased) — office,
 * warehouse, retail store, factory, etc. Deliberately independent of
 * {@code property}'s own {@code Property}/{@code Unit} entities: this
 * module has no dependency on {@code property} (see package-info), and a
 * tenant using Facilities does not need to also be a landlord using
 * Property — most SMEs occupying premises they lease from someone else
 * are exactly this module's target user, not Property's.
 */
@Entity
@Table(name = "facility_sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilitySite {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "site_type", nullable = false)
    private String siteType; // OFFICE, WAREHOUSE, RETAIL, FACTORY, OTHER

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> address;

    private String notes;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, CLOSED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static FacilitySite create(TenantId tenantId, String name, String siteType,
                                       Map<String, String> address, String notes) {
        FacilitySite s = new FacilitySite();
        s.tenantId = tenantId;
        s.name = name;
        s.siteType = siteType != null ? siteType.toUpperCase() : "OFFICE";
        s.address = address;
        s.notes = notes;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String name, String siteType, Map<String, String> address, String notes) {
        if (name != null) this.name = name;
        if (siteType != null) this.siteType = siteType.toUpperCase();
        if (address != null) this.address = address;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = "CLOSED";
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isActive() { return "ACTIVE".equals(status); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
