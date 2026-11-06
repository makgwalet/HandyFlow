package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a change to a position's allocated quantity (V600). Allocation reserves stock without changing
 * on-hand, so it is kept apart from WhseStockMovement, whose quantities must always add up to the on-hand balance.
 */
@Entity
@Table(name = "whse_allocation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseAllocationEvent {

    public static final String ALLOCATE = "ALLOCATE";
    public static final String DEALLOCATE = "DEALLOCATE";
    public static final String FULFIL = "FULFIL";

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;
    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;
    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 12)
    private String eventType;
    @Column(name = "qty", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal qty;
    @Column(name = "allocated_before", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal allocatedBefore;
    @Column(name = "allocated_after", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal allocatedAfter;
    @Column(name = "reference_type", updatable = false, length = 30)
    private String referenceType;
    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;
    @Column(name = "reference_number", updatable = false, length = 100)
    private String referenceNumber;
    @Column(name = "recorded_by_user_id", updatable = false)
    private UUID recordedByUserId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static WhseAllocationEvent record(UUID tenantId, UUID clientId, UUID itemId, UUID locationId,
                                             String eventType, BigDecimal qty, BigDecimal allocatedBefore,
                                             BigDecimal allocatedAfter, String referenceType, UUID referenceId,
                                             String referenceNumber, UUID recordedByUserId) {
        WhseAllocationEvent e = new WhseAllocationEvent();
        e.tenantId = tenantId;
        e.clientId = clientId;
        e.itemId = itemId;
        e.locationId = locationId;
        e.eventType = eventType;
        e.qty = qty;
        e.allocatedBefore = allocatedBefore;
        e.allocatedAfter = allocatedAfter;
        e.referenceType = referenceType;
        e.referenceId = referenceId;
        e.referenceNumber = referenceNumber;
        e.recordedByUserId = recordedByUserId;
        e.createdAt = Instant.now();
        return e;
    }
}
