package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One tanker and one driver carrying several loads in an order on one day. The stops are deliveries that point at the route. */
@Entity
@Table(name = "fuel_routes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelRoute {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "route_date", nullable = false)
    private LocalDate routeDate;

    @Column(length = 100)
    private String label;

    @Column(name = "tanker_id", nullable = false)
    private UUID tankerId;

    @Column(name = "driver_employee_id", nullable = false)
    private UUID driverEmployeeId;

    @Column(nullable = false, length = 12)
    private String status = "PLANNED";

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public static FuelRoute create(TenantId tenantId, LocalDate routeDate, String label, UUID tankerId, UUID driverEmployeeId, UUID by) {
        if (routeDate == null) throw new IllegalArgumentException("Pick the day of the route.");
        if (tankerId == null) throw new IllegalArgumentException("Pick a tanker.");
        if (driverEmployeeId == null) throw new IllegalArgumentException("Pick a driver.");
        FuelRoute r = new FuelRoute();
        r.tenantId = tenantId; r.routeDate = routeDate; r.tankerId = tankerId; r.driverEmployeeId = driverEmployeeId; r.createdBy = by;
        r.label = label == null || label.isBlank() ? null : (label.trim().length() > 100 ? label.trim().substring(0, 100) : label.trim());
        return r;
    }

    public boolean isOpen() { return "PLANNED".equals(status); }

    public void cancel(String reason) {
        if (!isOpen()) throw new IllegalStateException("This route is already cancelled.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for cancelling the route.");
        this.status = "CANCELLED"; this.cancelReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim(); this.cancelledAt = Instant.now();
    }
}
