package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fuel_tanker_compartments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTankerCompartment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tanker_id", nullable = false, updatable = false)
    private UUID tankerId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "capacity_litres", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal capacityLitres;

    public static FuelTankerCompartment create(TenantId tenantId, UUID tankerId, int number, BigDecimal capacity) {
        if (capacity == null || capacity.signum() <= 0) throw new IllegalArgumentException("Every compartment needs a capacity above zero.");
        FuelTankerCompartment c = new FuelTankerCompartment();
        c.tenantId = tenantId; c.tankerId = tankerId; c.number = number; c.capacityLitres = capacity;
        return c;
    }
}
