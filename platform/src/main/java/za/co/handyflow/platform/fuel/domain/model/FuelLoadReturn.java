package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fuel brought back on the truck and put into a tank. Append-only; never edited or deleted. Books no stock movement. */
@Entity
@Table(name = "fuel_load_returns")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelLoadReturn {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(nullable = false)
    private BigDecimal litres;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    public static FuelLoadReturn create(TenantId tenantId, UUID deliveryId, UUID tankId, BigDecimal litres, String note, UUID by, UUID clientRequestId) {
        if (tankId == null) throw new IllegalArgumentException("Pick the tank the fuel was put into.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres returned must be more than zero.");
        FuelLoadReturn r = new FuelLoadReturn();
        r.tenantId = tenantId; r.deliveryId = deliveryId; r.tankId = tankId; r.litres = litres;
        r.note = note == null || note.isBlank() ? null : (note.trim().length() > 500 ? note.trim().substring(0, 500) : note.trim());
        r.recordedBy = by; r.clientRequestId = clientRequestId;
        return r;
    }
}
