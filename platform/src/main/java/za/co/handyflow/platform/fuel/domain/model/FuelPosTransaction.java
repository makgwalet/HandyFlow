package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One live transaction from a pump/POS system: a SALE, or a REFUND or VOID that reverses (part of) an earlier sale.
 * RECEIVED until the shift it belongs to is accepted; then APPLIED (refunds and voids became approved shift adjustments) or NOT_APPLIED
 * (the shift was rejected, or was already accepted before this arrived). It never posts stock or money by itself.
 */
@Entity
@Table(name = "fuel_pos_transactions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPosTransaction {

    public static final String SALE = "SALE", REFUND = "REFUND", VOID = "VOID";
    public static final String RECEIVED = "RECEIVED", APPLIED = "APPLIED", NOT_APPLIED = "NOT_APPLIED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "key_id", updatable = false) private UUID keyId;
    @Column(nullable = false, updatable = false, length = 40) private String source;
    @Column(name = "external_id", nullable = false, updatable = false, length = 100) private String externalId;
    @Column(name = "shift_ref", nullable = false, updatable = false, length = 100) private String shiftRef;
    @Column(nullable = false, updatable = false, length = 10) private String kind;
    @Column(name = "original_external_id", updatable = false, length = 100) private String originalExternalId;
    @Column(name = "pump_label", updatable = false, length = 40) private String pumpLabel;
    @Column(updatable = false, precision = 12, scale = 3) private BigDecimal litres;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(updatable = false, length = 10) private String tender;
    @Column(name = "occurred_at", updatable = false) private Instant occurredAt;
    @Column(updatable = false, length = 300) private String reason;
    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64) private String payloadHash;
    @Column(nullable = false, length = 12) private String status = RECEIVED;
    @Column(columnDefinition = "TEXT") private String problems;
    @Column(name = "shift_id") private UUID shiftId;
    @Column(name = "adjustment_id") private UUID adjustmentId;
    @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt = Instant.now();

    public static FuelPosTransaction receive(TenantId tenantId, UUID keyId, String source, String shiftRef, String externalId, String kind,
                                             String originalExternalId, String pumpLabel, BigDecimal litres, BigDecimal amount, String tender,
                                             Instant occurredAt, String reason, String payloadHash) {
        FuelPosTransaction t = new FuelPosTransaction();
        t.tenantId = tenantId; t.keyId = keyId; t.source = source; t.shiftRef = shiftRef; t.externalId = externalId; t.kind = kind;
        t.originalExternalId = originalExternalId; t.pumpLabel = pumpLabel; t.litres = litres; t.amount = amount; t.tender = tender;
        t.occurredAt = occurredAt; t.reason = reason; t.payloadHash = payloadHash;
        return t;
    }

    public boolean isSale() { return SALE.equals(kind); }
    public boolean isReversal() { return REFUND.equals(kind) || VOID.equals(kind); }
    public boolean isReceived() { return RECEIVED.equals(status); }

    public void setProblems(String text) { this.problems = text == null || text.isBlank() ? null : text; }

    public void applied(UUID shiftId, UUID adjustmentId) { this.status = APPLIED; this.shiftId = shiftId; this.adjustmentId = adjustmentId; this.problems = null; }

    public void notApplied(String why) { this.status = NOT_APPLIED; this.problems = why; }
}
