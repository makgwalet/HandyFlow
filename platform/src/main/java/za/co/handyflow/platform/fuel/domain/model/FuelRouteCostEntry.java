package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An actual cost recorded against a route (what the vehicle, driver, tolls or anything else really cost).
 * Never edited or deleted: a wrong entry is voided with a reason and a new one is added.
 */
@Entity
@Table(name = "fuel_route_cost_entries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelRouteCostEntry {

    /** VEHICLE and DRIVER are compared with the estimate; TOLLS and OTHER are actual-only extras. */
    public static final Set<String> CATEGORIES = Set.of("VEHICLE", "DRIVER", "TOLLS", "OTHER");
    public static final Set<String> COMPARABLE = Set.of("VEHICLE", "DRIVER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(nullable = false)
    private BigDecimal amount;

    private BigDecimal quantity;

    @Column(length = 10)
    private String unit;

    @Column(length = 300)
    private String note;

    @Column(nullable = false, length = 8)
    private String status = "ACTIVE";

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public static FuelRouteCostEntry create(TenantId tenantId, UUID routeId, String category, BigDecimal amount, BigDecimal quantity, String unit, String note, UUID by) {
        if (category == null || !CATEGORIES.contains(category)) throw new IllegalArgumentException("Cost type must be one of Vehicle, Driver, Tolls or Other.");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("The amount must be zero or more.");
        if (quantity != null && quantity.signum() < 0) throw new IllegalArgumentException("The quantity cannot be negative.");
        if ("OTHER".equals(category) && (note == null || note.isBlank())) throw new IllegalArgumentException("Say what the cost was for when the type is Other.");
        FuelRouteCostEntry e = new FuelRouteCostEntry();
        e.tenantId = tenantId; e.routeId = routeId; e.category = category; e.amount = amount; e.quantity = quantity; e.recordedBy = by;
        e.unit = unit == null || unit.isBlank() ? null : (unit.trim().length() > 10 ? unit.trim().substring(0, 10) : unit.trim());
        e.note = note == null || note.isBlank() ? null : (note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        return e;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void voidEntry(String reason, UUID by) {
        if (!isActive()) throw new IllegalStateException("This cost entry is already voided.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for voiding the entry.");
        this.status = "VOIDED"; this.voidReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        this.voidedBy = by; this.voidedAt = Instant.now();
    }
}
