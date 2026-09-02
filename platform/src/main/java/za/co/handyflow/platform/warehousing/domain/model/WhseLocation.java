package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A storage location/bin within THIS OPERATOR's own warehouse — its
 * physical structure, not a per-client concept. Multiple clients' goods
 * can and normally do share the same warehouse's location set; which
 * client owns which stock at which location is captured on WhseInventory,
 * not here. Deliberately simpler than supplychain's ScStockLocation
 * (which models a tenant's own multi-site network) — this operator IS
 * one site's internal bin/zone layout, not multiple sites.
 */
@Entity
@Table(name = "whse_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseLocation {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false)
    private String code; // e.g. "A1-01"

    @Column(name = "zone")
    private String zone; // e.g. "Zone A", "Cold Storage" — free text, not modeled as a separate entity for this first pass

    @Column(name = "description")
    private String description;

    @Column(name = "capacity_units", precision = 12, scale = 3)
    private BigDecimal capacityUnits; // optional — max stock units this location can hold; null = untracked/unlimited

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static WhseLocation create(UUID tenantId, String code, String zone, String description,
                                       BigDecimal capacityUnits) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        WhseLocation l = new WhseLocation();
        l.tenantId = tenantId;
        l.code = code;
        l.zone = zone;
        l.description = description;
        l.capacityUnits = capacityUnits;
        l.active = true;
        l.createdAt = Instant.now();
        l.updatedAt = Instant.now();
        return l;
    }

    public void update(String code, String zone, String description, BigDecimal capacityUnits) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        this.code = code;
        this.zone = zone;
        this.description = description;
        this.capacityUnits = capacityUnits;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
