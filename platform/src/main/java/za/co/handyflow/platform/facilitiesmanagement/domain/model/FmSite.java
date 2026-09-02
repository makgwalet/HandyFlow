package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A client's premises the FM company services — unlike Module 5a's own
 * {@code FacilitySite} (the tenant's own premises), this always belongs
 * to a {@link FmClient}.
 */
@Entity
@Table(name = "fm_sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmSite {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

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

    @Version
    private Long version;

    public static FmSite create(TenantId tenantId, UUID clientId, String name, String siteType,
                                 Map<String, String> address, String notes) {
        FmSite s = new FmSite();
        s.tenantId = tenantId;
        s.clientId = clientId;
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
        this.address = address;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void close() { this.status = "CLOSED"; this.updatedAt = Instant.now(); }
    public void reopen() { this.status = "ACTIVE"; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
