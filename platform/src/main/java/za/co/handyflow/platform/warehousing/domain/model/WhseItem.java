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
 * One client's SKU/item master — deliberately this module's OWN item
 * catalogue, not a dependency on the shared `catalogue` module: a 3PL
 * client's SKUs belong to their own business, which is very often not a
 * HandyFlow tenant at all (same "clients aren't necessarily tenants"
 * reasoning behind this whole module). storageRatePerUnitPerMonth is the
 * finest-grained override in the resolution chain (item -&gt; client -&gt;
 * profile) — for a client whose catalogue mixes bulky and small items at
 * genuinely different storage costs.
 */
@Entity
@Table(name = "whse_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "uom", nullable = false)
    private String uom = "EACH"; // EACH | CASE | PALLET | KG | ... — free text, matching this codebase's established String-status convention rather than a closed enum for a client-defined unit

    @Column(name = "storage_rate_per_unit_per_month", precision = 12, scale = 4)
    private BigDecimal storageRatePerUnitPerMonth; // null = fall through to WhseClient then WhseProfile default

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static WhseItem create(UUID tenantId, UUID clientId, String sku, String description, String uom,
                                   BigDecimal storageRatePerUnitPerMonth) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        WhseItem i = new WhseItem();
        i.tenantId = tenantId;
        i.clientId = clientId;
        i.sku = sku.trim();
        i.description = description;
        i.uom = (uom == null || uom.isBlank()) ? "EACH" : uom;
        i.storageRatePerUnitPerMonth = storageRatePerUnitPerMonth;
        i.active = true;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public void update(String description, String uom, BigDecimal storageRatePerUnitPerMonth) {
        this.description = description;
        this.uom = (uom == null || uom.isBlank()) ? this.uom : uom;
        this.storageRatePerUnitPerMonth = storageRatePerUnitPerMonth;
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
