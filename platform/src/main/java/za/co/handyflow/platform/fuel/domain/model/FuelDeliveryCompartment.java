package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** How a load is split across the tanker's compartments. */
@Entity
@Table(name = "fuel_delivery_compartments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDeliveryCompartment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "compartment_id", nullable = false, updatable = false)
    private UUID compartmentId;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal litres;

    public static FuelDeliveryCompartment create(TenantId tenantId, UUID deliveryId, UUID compartmentId, BigDecimal litres) {
        FuelDeliveryCompartment d = new FuelDeliveryCompartment();
        d.tenantId = tenantId; d.deliveryId = deliveryId; d.compartmentId = compartmentId; d.litres = litres;
        return d;
    }
}
