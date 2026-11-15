package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A tanker a site accepts. When a site has any, only those tankers may serve it. */
@Entity
@Table(name = "fuel_site_approved_tankers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSiteApprovedTanker {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(name = "tanker_id", nullable = false, updatable = false)
    private UUID tankerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelSiteApprovedTanker of(TenantId tenantId, UUID siteId, UUID tankerId) {
        FuelSiteApprovedTanker a = new FuelSiteApprovedTanker();
        a.tenantId = tenantId; a.siteId = siteId; a.tankerId = tankerId;
        return a;
    }
}
