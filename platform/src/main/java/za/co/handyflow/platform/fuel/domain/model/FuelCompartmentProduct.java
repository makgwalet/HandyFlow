package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A product a tanker compartment is rated to carry. When a compartment has any, only those products may go in it. */
@Entity
@Table(name = "fuel_compartment_products")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelCompartmentProduct {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "compartment_id", nullable = false, updatable = false)
    private UUID compartmentId;

    @Column(name = "fuel_type", nullable = false, updatable = false, length = 30)
    private String fuelType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static String normalise(String fuelType) {
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Give a fuel type.");
        String t = fuelType.trim().toUpperCase();
        if (t.length() > 30) throw new IllegalArgumentException("A fuel type can be at most 30 characters.");
        return t;
    }

    public static FuelCompartmentProduct of(TenantId tenantId, UUID compartmentId, String fuelType) {
        FuelCompartmentProduct p = new FuelCompartmentProduct();
        p.tenantId = tenantId; p.compartmentId = compartmentId; p.fuelType = normalise(fuelType);
        return p;
    }
}
