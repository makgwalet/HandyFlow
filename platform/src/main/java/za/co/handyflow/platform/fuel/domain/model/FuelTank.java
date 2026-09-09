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

    // Fuel cost/margin engine — weighted-average cost, recalculated on
    // every receipt in addStock(). Nullable: a brand-new tank with no
    // receipts yet has no WAC. See V271's own migration comment for the
    // fuller design context.
    @Column(name = "cost_per_litre_wac", precision = 10, scale = 4)
    private BigDecimal costPerLitreWac;

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

    // FIX (fuel cost/margin engine, agreed design): now takes the
    // receipt's price so it can recalculate the tank's running
    // weighted-average cost on every fill-up. Standard WAC formula:
    // (currentLitres * currentWac + receivedLitres * receiptPrice) /
    // (currentLitres + receivedLitres). First-ever receipt on a tank
    // with no prior WAC just becomes the WAC outright (currentLitres is
    // 0, so the formula reduces to receiptPrice directly — no special
    // case needed).
    public BigDecimal addStock(BigDecimal litres, BigDecimal receiptPricePerLitre) {
        BigDecimal newLevel = this.currentLitres.add(litres);
        if (newLevel.compareTo(this.capacityLitres) > 0) {
            throw new IllegalArgumentException(
                    "Cannot add " + litres + "L — tank capacity (" +
                            capacityLitres + "L) would be exceeded. Current: " +
                            currentLitres + "L"
            );
        }
        BigDecimal existingValue = this.currentLitres.multiply(
                this.costPerLitreWac != null ? this.costPerLitreWac : BigDecimal.ZERO);
        BigDecimal incomingValue = litres.multiply(receiptPricePerLitre);
        this.costPerLitreWac = newLevel.compareTo(BigDecimal.ZERO) > 0
                ? existingValue.add(incomingValue).divide(newLevel, 4, java.math.RoundingMode.HALF_UP)
                : this.costPerLitreWac;
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