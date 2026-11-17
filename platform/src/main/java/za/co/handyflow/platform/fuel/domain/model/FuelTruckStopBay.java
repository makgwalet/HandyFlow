package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A named parking bay at the truck stop. A bay is switched off rather than deleted, so past stays keep their bay name. */
@Entity
@Table(name = "fuel_truck_stop_bays")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopBay {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelTruckStopBay of(TenantId tenantId, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Give the bay a name.");
        if (n.length() > 40) throw new IllegalArgumentException("The bay name can be at most 40 characters.");
        FuelTruckStopBay b = new FuelTruckStopBay();
        b.tenantId = tenantId; b.name = n;
        return b;
    }

    public void setActive(boolean active) { this.active = active; }
}
