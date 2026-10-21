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

    /** Everything an edit can change on an item, so create and update take the same shape. */
    public record Details(String name, String description, String sku, String barcode, String brand,
                          CatalogueItemType type, String unit, BigDecimal defaultPrice, BigDecimal costPrice,
                          BigDecimal vatRate, boolean vatExempt) {}

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

    private String sku;

    private String barcode;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private CatalogueItemType itemType = CatalogueItemType.PRODUCT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogueItemStatus status = CatalogueItemStatus.ACTIVE;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(nullable = false)
    private String unit;

    @Column(name = "default_price", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "min_price", precision = 15, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "variant_key", length = 400)
    private String variantKey;

    @Column(name = "variant_label", length = 300)
    private String variantLabel;

    @Column(name = "variant_values", length = 1100)
    private String variantValues;

    @Column(name = "vat_rate", nullable = false,
            precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_exempt", nullable = false)
    private boolean vatExempt;

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

    /** New items start ACTIVE unless a draft is asked for. The service resolves the VAT rate before calling. */
    public static CatalogueItem create(TenantId tenantId, CatalogueCategory category, Details d, CatalogueItemStatus status) {
        CatalogueItem item = new CatalogueItem();
        item.tenantId = tenantId;
        item.category = category;
        item.apply(d);
        item.status = status == null ? CatalogueItemStatus.ACTIVE : status;
        item.active = item.status.sellable();
        item.createdAt = Instant.now();
        item.updatedAt = item.createdAt;
        return item;
    }

    public void update(CatalogueCategory category, Details d) {
        this.category = category;
        apply(d);
        this.updatedAt = Instant.now();
    }

    private void apply(Details d) {
        this.name = d.name();
        this.description = d.description();
        this.sku = d.sku();
        this.barcode = d.barcode();
        this.brand = d.brand();
        this.itemType = d.type() == null ? CatalogueItemType.PRODUCT : d.type();
        this.unit = d.unit();
        this.defaultPrice = d.defaultPrice();
        this.costPrice = d.costPrice();
        this.vatExempt = d.vatExempt();
        this.vatRate = d.vatExempt() ? new BigDecimal("0.00") : d.vatRate();
    }

    /** Makes a new item a variant of the parent. Only called while creating the variant. */
    public void asVariant(UUID parentId, String key, String label, String values) {
        this.parentId = parentId;
        this.variantKey = key;
        this.variantLabel = label;
        this.variantValues = values;
    }

    public boolean isVariant() { return parentId != null; }

    /** The floor under every price of this item; null means none. */
    public void changeMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
        this.updatedAt = Instant.now();
    }

    /** The Retail price that Invoicing, POS and Supply Chain read. Only the pricing workflow calls this. */
    public void changeDefaultPrice(BigDecimal price) {
        this.defaultPrice = price;
        this.updatedAt = Instant.now();
    }

    /** Moves the item through its lifecycle. The caller has already checked the move with CatalogueRules. */
    public void changeStatus(CatalogueItemStatus to, String reason) {
        this.status = to;
        this.statusReason = reason;
        this.statusChangedAt = Instant.now();
        this.active = to.sellable();
        this.updatedAt = this.statusChangedAt;
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.status = CatalogueItemStatus.ARCHIVED;
        this.statusChangedAt = this.deletedAt;
        this.active = false;
        this.updatedAt = this.deletedAt;
    }

    public boolean isDeleted() { return deletedAt != null; }

    public CatalogueRules.Snapshot snapshot() {
        return new CatalogueRules.Snapshot(name, description, sku, barcode, brand, itemType.name(), unit,
                category == null ? null : category.getName(), defaultPrice, costPrice, vatRate, vatExempt);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
