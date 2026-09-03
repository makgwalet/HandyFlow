package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalogue_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CatalogueItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CatalogueCategory category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(name = "default_price", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "vat_rate", nullable = false,
            precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "is_active", nullable = false)
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

    public static CatalogueItem create(TenantId tenantId,
                                       CatalogueCategory category,
                                       String name,
                                       String description,
                                       String unit,
                                       BigDecimal defaultPrice,
                                       BigDecimal vatRate) {
        CatalogueItem item = new CatalogueItem();
        item.tenantId = tenantId;
        item.category = category;
        item.name = name.trim();
        item.description = description;
        item.unit = unit;
        item.defaultPrice = defaultPrice;
        // FIX (VAT consolidation pass): CatalogueService.createItem() —
        // this factory's only real caller, confirmed by search — now
        // always resolves a concrete default via VatRateProvider before
        // calling here, so this fallback is no longer reachable through
        // the actual application flow. Left in place, not deleted or
        // wired to VatRateProvider itself: a domain entity's static
        // factory shouldn't reach into Spring-managed config (matches
        // this codebase's own convention of keeping entities free of
        // framework dependencies), and this remains a reasonable,
        // harmless defensive default for any future direct caller that
        // doesn't resolve one first.
        item.vatRate = vatRate != null ? vatRate : new BigDecimal("15.00");
        item.active = true;
        item.createdAt = Instant.now();
        item.updatedAt = Instant.now();
        return item;

    }

    public void update(String name, String description, String unit,
                       BigDecimal defaultPrice, BigDecimal vatRate,
                       CatalogueCategory category) {
        this.name = name.trim();
        this.description = description;
        this.unit = unit;
        this.defaultPrice = defaultPrice;
        this.vatRate = vatRate;
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void update(String name, String description, CatalogueCategory category,
                       String unit, BigDecimal defaultPrice, BigDecimal vatRate) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.unit = unit;
        this.defaultPrice = defaultPrice;
        this.vatRate = vatRate;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }


}
