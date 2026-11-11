package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A refund request against one payment. REQUESTED reserves the amount (so two
 * refunds can't together exceed what was received) until it is COMPLETED —
 * money actually returned — or FAILED, which frees the amount again.
 */
@Entity
@Table(name = "event_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRefund {

    public static final String REQUESTED = "REQUESTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)    private UUID tenantId;
    @Column(name = "event_id", nullable = false)     private UUID eventId;
    @Column(name = "payment_id", nullable = false)   private UUID paymentId;
    @Column(name = "amount", nullable = false)       private BigDecimal amount;
    @Column(name = "reason", nullable = false)       private String reason;
    @Column(name = "status", nullable = false)       private String status = REQUESTED;
    @Column(name = "provider_ref")                   private String providerRef;
    @Column(name = "requested_by_name")              private String requestedByName;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "completed_at")                   private Instant completedAt;
    @Column(name = "failure_reason")                 private String failureReason;

    public static EventRefund requested(TenantId tenantId, UUID eventId, UUID paymentId,
                                        BigDecimal amount, String reason, String requestedByName) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        EventRefund r = new EventRefund();
        r.id              = UUID.randomUUID();
        r.tenantId        = tenantId.getValue();
        r.eventId         = eventId;
        r.paymentId       = paymentId;
        r.amount          = amount.setScale(2, RoundingMode.HALF_UP);
        r.reason          = reason;
        r.status          = REQUESTED;
        r.requestedByName = requestedByName;
        r.requestedAt     = Instant.now();
        return r;
    }

    public boolean isRequested() {
        return REQUESTED.equals(status);
    }

    public void attachProviderRef(String ref) {
        this.providerRef = ref;
    }

    public void complete(String providerRef) {
        if (!isRequested())
            throw new IllegalStateException("Only a REQUESTED refund can be completed (status is " + status + ")");
        this.status      = COMPLETED;
        this.completedAt = Instant.now();
        if (providerRef != null) this.providerRef = providerRef;
    }

    public void fail(String reason) {
        if (!isRequested())
            throw new IllegalStateException("Only a REQUESTED refund can be failed (status is " + status + ")");
        this.status        = FAILED;
        this.failureReason = reason;
    }
}