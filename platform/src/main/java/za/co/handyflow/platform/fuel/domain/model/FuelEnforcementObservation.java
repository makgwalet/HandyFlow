package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** What a control would have refused while it was only watching. Recorded for review; it never blocked anything. */
@Entity
@Table(name = "fuel_enforcement_observations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelEnforcementObservation {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 30)
    private String control;

    @Column(name = "delivery_id", updatable = false)
    private UUID deliveryId;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelEnforcementObservation of(TenantId t, String control, UUID deliveryId, String message, Instant at) {
        FuelEnforcementObservation o = new FuelEnforcementObservation();
        o.tenantId = t; o.control = control; o.deliveryId = deliveryId; o.createdAt = at;
        o.message = message == null ? "" : (message.length() > 500 ? message.substring(0, 500) : message);
        return o;
    }
}
