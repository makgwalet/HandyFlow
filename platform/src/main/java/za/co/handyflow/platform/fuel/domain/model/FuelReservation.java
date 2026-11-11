package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stock set aside for a customer or order. It does NOT change the physical
 * tank balance; it only reduces what can be promised to anyone else.
 */
@Entity
@Table(name = "fuel_reservations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelReservation {

    public static final String ACTIVE = "ACTIVE", RELEASED = "RELEASED", CONSUMED = "CONSUMED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false, updatable = false)
    private UUID tankId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal litres;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "reference_type", length = 20, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(nullable = false, length = 12)
    private String status = ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(length = 300)
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    public static FuelReservation create(TenantId tenantId, UUID tankId, BigDecimal litres, UUID customerId,
                                         String referenceType, UUID referenceId, Instant expiresAt,
                                         String note, UUID by) {
        FuelReservation r = new FuelReservation();
        r.tenantId = tenantId; r.tankId = tankId; r.litres = litres; r.customerId = customerId;
        r.referenceType = referenceType; r.referenceId = referenceId; r.expiresAt = expiresAt;
        r.note = note; r.createdBy = by; r.createdAt = Instant.now();
        return r;
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public void release() {
        if (!isActive()) throw new IllegalStateException("This reservation is already " + status.toLowerCase() + ".");
        this.status = RELEASED; this.closedAt = Instant.now();
    }

    public void consume() {
        if (!isActive()) throw new IllegalStateException("This reservation is already " + status.toLowerCase() + ".");
        this.status = CONSUMED; this.closedAt = Instant.now();
    }
}
