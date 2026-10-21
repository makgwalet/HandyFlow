package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A named set of prices (Retail, Wholesale, Trade). The default list is Retail: its prices are the items' default prices. */
@Getter
@Entity
@Table(name = "catalogue_price_lists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CataloguePriceList {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean defaultList;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    public static CataloguePriceList create(TenantId tenantId, String code, String name, boolean defaultList, int sortOrder) {
        CataloguePriceList l = new CataloguePriceList();
        l.tenantId = tenantId;
        l.code = code;
        l.name = name;
        l.defaultList = defaultList;
        l.sortOrder = sortOrder;
        l.createdAt = Instant.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public void update(String name, int sortOrder, boolean active) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID by) {
        this.deletedAt = Instant.now();
        this.deletedBy = by;
        this.updatedAt = this.deletedAt;
    }
}
