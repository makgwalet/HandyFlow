package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_transfers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTransfer {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "from_tank_id", nullable = false, updatable = false)
    private UUID fromTankId;

    @Column(name = "to_tank_id", nullable = false, updatable = false)
    private UUID toTankId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal litres;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FuelTransfer of(TenantId tenantId, UUID from, UUID to, BigDecimal litres, String reason, UUID by) {
        FuelTransfer t = new FuelTransfer();
        t.tenantId = tenantId; t.fromTankId = from; t.toTankId = to;
        t.litres = litres; t.reason = reason; t.createdBy = by; t.createdAt = Instant.now();
        return t;
    }
}
