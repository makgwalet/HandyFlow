package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Catalogue settings of one tenant (not a global application setting). A tenant with no row is treated as
 * "price approval required": existing tenants were given an explicit "not required" row by the migration.
 */
@Getter
@Entity
@Table(name = "catalogue_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueSetting {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "price_approval_required", nullable = false)
    private boolean priceApprovalRequired;

    @Column(name = "item_approval_required", nullable = false)
    private boolean itemApprovalRequired;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static CatalogueSetting of(UUID tenantId, boolean priceApprovalRequired, UUID by) {
        CatalogueSetting s = new CatalogueSetting();
        s.tenantId = tenantId;
        s.priceApprovalRequired = priceApprovalRequired;
        s.updatedAt = Instant.now();
        s.updatedBy = by;
        return s;
    }

    public void setItemApproval(boolean itemApprovalRequired, UUID by) {
        this.itemApprovalRequired = itemApprovalRequired;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    public void set(boolean priceApprovalRequired, UUID by) {
        this.priceApprovalRequired = priceApprovalRequired;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }
}
