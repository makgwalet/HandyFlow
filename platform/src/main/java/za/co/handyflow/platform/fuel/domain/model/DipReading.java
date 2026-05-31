// fuel/domain/model/DipReading.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_dip_readings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DipReading {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    @Column(name = "actual_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualLitres;

    @Column(name = "calculated_litres", precision = 12, scale = 2)
    private BigDecimal calculatedLitres;

    @Column(name = "variance_litres", precision = 12, scale = 2)
    private BigDecimal varianceLitres;

    @Column(name = "read_by")
    private String readBy;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DipReading create(TenantId tenantId, UUID tankId,
                                    Instant readAt, BigDecimal actualLitres,
                                    BigDecimal calculatedLitres, String readBy,
                                    String notes) {
        DipReading d = new DipReading();
        d.tenantId          = tenantId;
        d.tankId            = tankId;
        d.readAt            = readAt;
        d.actualLitres      = actualLitres;
        d.calculatedLitres  = calculatedLitres;
        // WHY calculated - actual? Positive = we have more than expected (good)
        // Negative = fuel is missing (theft/leak — needs investigation)
        d.varianceLitres    = calculatedLitres != null
                ? calculatedLitres.subtract(actualLitres)
                : null;
        d.readBy            = readBy;
        d.notes             = notes;
        d.createdAt         = Instant.now();
        return d;
    }

    public boolean hasNegativeVariance() {
        return varianceLitres != null &&
                varianceLitres.compareTo(BigDecimal.ZERO) < 0;
    }
}