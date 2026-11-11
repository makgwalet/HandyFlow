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
 * One payment attempt for one reserved ticket.
 * <p>
 * PENDING -> PAID | FAILED | EXPIRED | CANCELLED | REQUIRES_REFUND
 * FAILED / EXPIRED / CANCELLED -> PAID | REQUIRES_REFUND   (a payment can arrive late)
 * PAID -> REQUIRES_REFUND                                   (ticket cancelled after payment)
 * <p>
 * receivedAmount is what actually arrived and is the ONLY basis for refunds and
 * revenue — never the price that was asked for. The two differ exactly when a
 * payer sends the wrong amount, which is why REQUIRES_REFUND exists.
 * <p>
 * @Version adds optimistic locking as a second guard on top of the event-row
 * lock every state change takes: a stale writer fails loudly instead of
 * silently overwriting a status.
 */
@Entity
@Table(name = "event_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventPayment {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REQUIRES_REFUND = "REQUIRES_REFUND";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)        private UUID tenantId;
    @Column(name = "event_id", nullable = false)         private UUID eventId;
    @Column(name = "guest_id", nullable = false)         private UUID guestId;
    @Column(name = "amount", nullable = false)           private BigDecimal amount;
    @Column(name = "received_amount")                    private BigDecimal receivedAmount;
    @Column(name = "fee_amount", nullable = false)       private BigDecimal feeAmount = BigDecimal.ZERO;
    @Column(name = "refunded_amount", nullable = false)  private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(name = "currency", nullable = false)         private String currency = "ZAR";
    @Column(name = "provider", nullable = false)         private String provider;
    @Column(name = "provider_ref")                       private String providerRef;
    @Column(name = "status", nullable = false)           private String status = PENDING;
    @Column(name = "expires_at", nullable = false)       private Instant expiresAt;
    @Column(name = "paid_at")                            private Instant paidAt;
    @Column(name = "failure_reason")                     private String failureReason;
    @Column(name = "review_reason")                      private String reviewReason;

    @Version
    @Column(name = "version", nullable = false)          private long version;

    @Column(name = "created_at", nullable = false)       private Instant createdAt;
    @Column(name = "updated_at", nullable = false)       private Instant updatedAt;

    public static EventPayment pending(TenantId tenantId, UUID eventId, UUID guestId,
                                       BigDecimal amount, String provider, Instant expiresAt) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        EventPayment p = new EventPayment();
        p.id        = UUID.randomUUID();
        p.tenantId  = tenantId.getValue();
        p.eventId   = eventId;
        p.guestId   = guestId;
        p.amount    = money(amount);
        p.provider  = provider;
        p.expiresAt = expiresAt;
        p.status    = PENDING;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** True while we are holding the payer's money: it was received and has not all been given back. */
    public boolean isMoneyHeld() {
        return PAID.equals(status) || REQUIRES_REFUND.equals(status);
    }

    /** What can still be refunded: received minus already refunded. Zero if no money is held. */
    public BigDecimal refundable() {
        if (!isMoneyHeld() || receivedAmount == null) return BigDecimal.ZERO.setScale(2);
        return receivedAmount.subtract(refundedAmount).max(BigDecimal.ZERO.setScale(2));
    }

    public void attachProviderRef(String ref) {
        if (!isPending())
            throw new IllegalStateException("Only a PENDING payment can be given a provider reference (status is " + status + ")");
        this.providerRef = ref;
        touch();
    }

    /** Money matching the price has arrived. Allowed from PENDING, and late from FAILED/EXPIRED/CANCELLED. */
    public void markPaid(Instant at, BigDecimal received, BigDecimal fee) {
        if (!canReceiveMoney())
            throw new IllegalStateException("A " + status + " payment cannot be marked paid");
        this.status         = PAID;
        this.receivedAmount = money(received);
        this.feeAmount      = fee == null ? BigDecimal.ZERO.setScale(2) : money(fee);
        this.paidAt         = at;
        this.failureReason  = null;
        touch();
    }

    /** Money arrived but the ticket can't be honoured — wrong amount, or paid late with no seat left. A refund is owed. */
    public void receivedButUnhonoured(Instant at, BigDecimal received, BigDecimal fee, String reason) {
        if (!canReceiveMoney())
            throw new IllegalStateException("A " + status + " payment cannot be flagged for refund this way");
        this.status         = REQUIRES_REFUND;
        this.receivedAmount = money(received);
        this.feeAmount      = fee == null ? BigDecimal.ZERO.setScale(2) : money(fee);
        this.paidAt         = at;
        this.reviewReason   = reason;
        touch();
    }

    /** The ticket was cancelled after it had been paid for: the money is now owed back (per policy, possibly in part). */
    public void flagRefundDue(String reason) {
        if (!PAID.equals(status))
            throw new IllegalStateException("Only a PAID payment can be flagged as refund due (status is " + status + ")");
        this.status       = REQUIRES_REFUND;
        this.reviewReason = reason;
        touch();
    }

    public void fail(String reason) {
        requirePending("failed");
        this.status        = FAILED;
        this.failureReason = reason;
        touch();
    }

    public void expire() {
        requirePending("expired");
        this.status = EXPIRED;
        touch();
    }

    public void cancel(String reason) {
        requirePending("cancelled");
        this.status        = CANCELLED;
        this.failureReason = reason;
        touch();
    }

    /** Records money actually handed back. Never more than is still refundable. */
    public void applyRefund(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        if (amount.compareTo(refundable()) > 0)
            throw new IllegalArgumentException("Refund of " + amount + " is more than the " + refundable() + " still refundable");
        this.refundedAmount = refundedAmount.add(money(amount));
        touch();
    }

    /** The ticket this money paid for moved to a new holder: the payment follows the ticket, so refunds find the right guest. */
    public void reassignGuest(UUID newGuestId) {
        if (newGuestId == null) throw new IllegalArgumentException("A guest is required");
        if (!isMoneyHeld())
            throw new IllegalStateException("Only a payment holding money can move to another guest (status is " + status + ")");
        this.guestId = newGuestId;
        touch();
    }

    private boolean canReceiveMoney() {
        return PENDING.equals(status) || EXPIRED.equals(status) || CANCELLED.equals(status) || FAILED.equals(status);
    }

    private void requirePending(String verb) {
        if (!isPending())
            throw new IllegalStateException("Only a PENDING payment can be " + verb + " (status is " + status + ")");
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}