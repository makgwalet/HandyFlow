package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A Bookings service the truck stop sells (parking, shower, wash, weighbridge...). The service itself, its price, hours and resources stay in Bookings. */
@Entity
@Table(name = "fuel_truck_stop_offerings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopOffering {

    public static final Set<String> KINDS = Set.of("PARKING", "SHOWER", "WASH", "WEIGHBRIDGE", "WORKSHOP", "FOOD", "OTHER");

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "bookings_service_id", nullable = false, updatable = false) private UUID bookingsServiceId;
    @Column(nullable = false, length = 12) private String kind;
    @Column(name = "depot_id") private UUID depotId;
    @Column(nullable = false, length = 10) private String status = "ACTIVE";
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private Long version;

    public static FuelTruckStopOffering create(TenantId tenantId, UUID bookingsServiceId, String kind, UUID depotId, UUID by) {
        if (bookingsServiceId == null) throw new IllegalArgumentException("Choose the Bookings service this offering sells.");
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Choose a kind: " + String.join(", ", new java.util.TreeSet<>(KINDS)) + ".");
        FuelTruckStopOffering o = new FuelTruckStopOffering();
        o.tenantId = tenantId; o.bookingsServiceId = bookingsServiceId; o.kind = k; o.depotId = depotId; o.createdBy = by;
        return o;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void setActive(boolean active) { this.status = active ? "ACTIVE" : "INACTIVE"; this.updatedAt = Instant.now(); }
}
