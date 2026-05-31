// earthmoving/domain/model/MaintenanceRecord.java

package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "earthmoving_maintenance")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MaintenanceRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String description;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "hours_at_service", precision = 10, scale = 1)
    private BigDecimal hoursAtService;

    @Column(name = "next_service_hours", precision = 10, scale = 1)
    private BigDecimal nextServiceHours;

    @Column(precision = 15, scale = 2)
    private BigDecimal cost;

    private String supplier;

    @Column(name = "invoice_ref")
    private String invoiceRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static MaintenanceRecord create(TenantId tenantId, UUID assetId,
                                           String type, String description,
                                           Instant performedAt, BigDecimal hoursAtService,
                                           BigDecimal cost, String supplier,
                                           String invoiceRef) {
        MaintenanceRecord r = new MaintenanceRecord();
        r.tenantId       = tenantId;
        r.assetId        = assetId;
        r.type           = type;
        r.description    = description;
        r.performedAt    = performedAt;
        r.hoursAtService = hoursAtService;
        r.cost           = cost;
        r.supplier       = supplier;
        r.invoiceRef     = invoiceRef;
        r.createdAt      = Instant.now();
        r.updatedAt      = Instant.now();
        return r;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}