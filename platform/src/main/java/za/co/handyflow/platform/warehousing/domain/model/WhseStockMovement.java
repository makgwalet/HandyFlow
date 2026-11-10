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
 * Append-only audit trail of every quantity change to a WhseInventory
 * position — same immutability rationale as every other ledger in this
 * engagement (CollAgencyTrustTransaction, debtcollection's contact log):
 * a movement is corrected with a new offsetting entry, never edited in
 * place. qtyChange is signed (positive for RECEIPT/ADJUSTMENT-in,
 * negative for PICK/ADJUSTMENT-out); qtyBefore/qtyAfter snapshot the
 * on-hand balance at the moment of the movement for audit purposes.
 */
@Entity
@Table(name = "whse_stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseStockMovement {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "movement_type", nullable = false)
    private String movementType; // RECEIPT | PICK | ADJUSTMENT | QUARANTINE | RELEASE | DAMAGE (more allowed by V599)

    @Column(name = "qty_change", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyChange; // signed

    @Column(name = "qty_before", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyBefore;

    @Column(name = "qty_after", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyAfter;

    @Column(name = "reference_type")
    private String referenceType; // INBOUND_SHIPMENT | OUTBOUND_ORDER | ADJUSTMENT

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The stock status of the position this movement belongs to (V600). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "AVAILABLE";

    /** The lot the movement belongs to, for lot-tracked items (V608). */
    @Column(name = "lot_id")
    private UUID lotId;

    /** Optional caller-supplied key: a retried request with the same key is recorded once (V599). */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    public static WhseStockMovement record(UUID tenantId, UUID clientId, UUID itemId, UUID locationId,
                                           String movementType, BigDecimal qtyChange, BigDecimal qtyBefore,
                                           BigDecimal qtyAfter, String referenceType, UUID referenceId,
                                           String referenceNumber, String notes, UUID recordedByUserId) {
        return record(tenantId, clientId, itemId, locationId, movementType, qtyChange, qtyBefore, qtyAfter,
                referenceType, referenceId, referenceNumber, notes, recordedByUserId, "AVAILABLE", null);
    }

    public static WhseStockMovement record(UUID tenantId, UUID clientId, UUID itemId, UUID locationId,
                                           String movementType, BigDecimal qtyChange, BigDecimal qtyBefore,
                                           BigDecimal qtyAfter, String referenceType, UUID referenceId,
                                           String referenceNumber, String notes, UUID recordedByUserId,
                                           String status, String idempotencyKey) {
        return record(tenantId, clientId, itemId, locationId, movementType, qtyChange, qtyBefore, qtyAfter,
                referenceType, referenceId, referenceNumber, notes, recordedByUserId, status, idempotencyKey, null);
    }

    public static WhseStockMovement record(UUID tenantId, UUID clientId, UUID itemId, UUID locationId,
                                           String movementType, BigDecimal qtyChange, BigDecimal qtyBefore,
                                           BigDecimal qtyAfter, String referenceType, UUID referenceId,
                                           String referenceNumber, String notes, UUID recordedByUserId,
                                           String status, String idempotencyKey, UUID lotId) {
        if (qtyChange == null || qtyChange.signum() == 0) {
            throw new IllegalArgumentException("qtyChange must be non-zero");
        }
        WhseStockMovement m = new WhseStockMovement();
        m.tenantId = tenantId;
        m.clientId = clientId;
        m.itemId = itemId;
        m.locationId = locationId;
        m.movementType = movementType;
        m.qtyChange = qtyChange;
        m.qtyBefore = qtyBefore;
        m.qtyAfter = qtyAfter;
        m.referenceType = referenceType;
        m.referenceId = referenceId;
        m.referenceNumber = referenceNumber;
        m.notes = notes;
        m.recordedByUserId = recordedByUserId;
        m.status = status;
        m.idempotencyKey = idempotencyKey;
        m.lotId = lotId;
        m.createdAt = Instant.now();
        return m;
    }
}
