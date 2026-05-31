// property/domain/model/Unit.java

package za.co.handyflow.platform.property.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "property_units")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Unit {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false)
    private String unitNumber;

    @Column(name = "unit_type", nullable = false)
    private String unitType;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(name = "size_sqm", precision = 10, scale = 2)
    private BigDecimal sizeSqm;

    @Column(name = "base_rent", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseRent;

    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(nullable = false)
    private String status = "VACANT";

    @Column(nullable = false)
    private boolean furnished = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> amenities;

    private String notes;

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

    public static Unit create(TenantId tenantId, Property property,
                              String unitNumber, String unitType,
                              Integer floorNumber, BigDecimal sizeSqm,
                              BigDecimal baseRent, BigDecimal depositAmount,
                              boolean furnished) {
        Unit u = new Unit();
        u.tenantId      = tenantId;
        u.property      = property;
        u.unitNumber    = unitNumber.trim().toUpperCase();
        u.unitType      = unitType.toUpperCase();
        u.floorNumber   = floorNumber;
        u.sizeSqm       = sizeSqm;
        u.baseRent      = baseRent;
        u.depositAmount = depositAmount;
        u.furnished     = furnished;
        u.status        = "VACANT";
        u.createdAt     = Instant.now();
        u.updatedAt     = Instant.now();
        return u;
    }

    public void occupy() {
        this.status    = "OCCUPIED";
        this.updatedAt = Instant.now();
    }

    public void vacate() {
        this.status    = "VACANT";
        this.updatedAt = Instant.now();
    }

    public void setMaintenance() {
        this.status    = "MAINTENANCE";
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted()  { return deletedAt != null; }
    public boolean isVacant()   { return "VACANT".equals(status); }
    public boolean isOccupied() { return "OCCUPIED".equals(status); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}