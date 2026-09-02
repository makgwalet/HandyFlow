package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The top-level farm record for a tenant. A tenant running more than one
 * physical farm is simply more than one {@code AgFarm} row — matching how
 * {@code fleet}'s tenant owns multiple {@code Vehicle}s, not a separate
 * "multi-farm operation" concept. See the module's own package-info.java
 * for why this module has no internal-vs-provider split question at all.
 * <p>
 * Entity convention matches {@code earthmoving.MaintenanceRecord} exactly
 * (plain {@code @Entity}, embedded {@code TenantId}, boxed
 * {@code @Version Long}, manual {@code createdAt}/{@code updatedAt}) —
 * confirmed against the real file before writing this one.
 */
@Entity
@Table(name = "ag_farms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgFarm {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    /** LIVESTOCK | CROP | POULTRY | AQUACULTURE | MIXED — Increment 1 in practice is always LIVESTOCK or MIXED. */
    @Column(name = "farm_type", nullable = false)
    private String farmType;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String province;

    private String region;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "total_hectares", precision = 12, scale = 2)
    private BigDecimal totalHectares;

    /** Nullable — references an {@code HrFacade} employee id, validated at the service layer, snapshot below. */
    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "manager_name")
    private String managerName;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static AgFarm create(TenantId tenantId, String name, String farmType, String registrationNumber,
                                 String province, String region, BigDecimal totalHectares) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (farmType == null || farmType.isBlank()) throw new IllegalArgumentException("farmType is required");

        AgFarm f = new AgFarm();
        f.tenantId = tenantId;
        f.name = name;
        f.farmType = farmType;
        f.registrationNumber = registrationNumber;
        f.province = province;
        f.region = region;
        f.totalHectares = totalHectares;
        f.createdAt = Instant.now();
        f.updatedAt = Instant.now();
        return f;
    }

    public void update(String name, String farmType, String registrationNumber, String province, String region,
                        Double gpsLatitude, Double gpsLongitude, BigDecimal totalHectares, String notes) {
        if (name != null && !name.isBlank()) this.name = name;
        if (farmType != null && !farmType.isBlank()) this.farmType = farmType;
        this.registrationNumber = registrationNumber;
        this.province = province;
        this.region = region;
        this.gpsLatitude = gpsLatitude;
        this.gpsLongitude = gpsLongitude;
        this.totalHectares = totalHectares;
        this.notes = notes;
    }

    public void assignManager(UUID managerId, String managerName) {
        this.managerId = managerId;
        this.managerName = managerName;
    }

    public void clearManager() {
        this.managerId = null;
        this.managerName = null;
    }

    public void deactivate() { this.status = "INACTIVE"; }

    public void reactivate() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
