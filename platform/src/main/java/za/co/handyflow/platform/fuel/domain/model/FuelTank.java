// fuel/domain/model/FuelTank.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_tanks")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTank {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    @Column(name = "capacity_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal capacityLitres;

    @Column(name = "current_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentLitres = BigDecimal.ZERO;

    private String location;
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

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

    public static FuelTank create(TenantId tenantId, String name, String fuelType,
                                  BigDecimal capacityLitres, String location) {
        FuelTank t = new FuelTank();
        t.tenantId       = tenantId;
        t.name           = name.trim();
        t.fuelType       = fuelType.toUpperCase();
        t.capacityLitres = capacityLitres;
        t.currentLitres  = BigDecimal.ZERO;
        t.location       = location;
        t.active         = true;
        t.createdAt      = Instant.now();
        t.updatedAt      = Instant.now();
        return t;
    }

    public BigDecimal addStock(BigDecimal litres) {
        BigDecimal newLevel = this.currentLitres.add(litres);
        if (newLevel.compareTo(this.capacityLitres) > 0) {
            throw new IllegalArgumentException(
                    "Cannot add " + litres + "L — tank capacity (" +
                            capacityLitres + "L) would be exceeded. Current: " +
                            currentLitres + "L"
            );
        }
        this.currentLitres = newLevel;
        this.updatedAt     = Instant.now();
        return this.currentLitres;
    }

    public BigDecimal removeStock(BigDecimal litres) {
        if (litres.compareTo(this.currentLitres) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient fuel. Requested: " + litres +
                            "L, Available: " + currentLitres + "L"
            );
        }
        this.currentLitres = this.currentLitres.subtract(litres);
        this.updatedAt     = Instant.now();
        return this.currentLitres;
    }

    public BigDecimal getFillPercentage() {
        if (capacityLitres.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentLitres
                .multiply(new BigDecimal("100"))
                .divide(capacityLitres, 1, java.math.RoundingMode.HALF_UP);
    }

    public boolean isLow() {
        // WHY 20%? Industry standard low-fuel alert threshold
        return getFillPercentage().compareTo(new BigDecimal("20")) <= 0;
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