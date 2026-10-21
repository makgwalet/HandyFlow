package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** One part of a package and how many of it the package holds. */
@Getter
@Entity
@Table(name = "catalogue_bundle_components")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueBundleComponent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "bundle_item_id", nullable = false, updatable = false)
    private UUID bundleItemId;

    @Column(name = "component_item_id", nullable = false, updatable = false)
    private UUID componentItemId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static CatalogueBundleComponent of(TenantId tenantId, UUID bundleItemId, UUID componentItemId, BigDecimal quantity, int sortOrder) {
        var c = new CatalogueBundleComponent();
        c.tenantId = tenantId;
        c.bundleItemId = bundleItemId;
        c.componentItemId = componentItemId;
        c.quantity = quantity;
        c.sortOrder = sortOrder;
        return c;
    }
}
