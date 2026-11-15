package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Carrying {@code toFuel} in a compartment that last carried {@code fromFuel} needs a cleaning recorded after that cargo. The company decides which pairs. */
@Entity
@Table(name = "fuel_changeover_rules")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelChangeoverRule {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "from_fuel", nullable = false, updatable = false, length = 30)
    private String fromFuel;

    @Column(name = "to_fuel", nullable = false, updatable = false, length = 30)
    private String toFuel;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelChangeoverRule of(TenantId tenantId, String from, String to, String note) {
        String f = FuelCompartmentProduct.normalise(from), t = FuelCompartmentProduct.normalise(to);
        if (f.equals(t)) throw new IllegalArgumentException("A changeover is from one product to a different one.");
        FuelChangeoverRule r = new FuelChangeoverRule();
        r.tenantId = tenantId; r.fromFuel = f; r.toFuel = t;
        r.note = note == null || note.isBlank() ? null : (note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        return r;
    }
}
