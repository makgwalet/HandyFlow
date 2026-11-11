package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPaymentRepository extends JpaRepository<EventPayment, UUID> {

    @Query("SELECT p.tenantId AS tenantId, p.eventId AS eventId FROM EventPayment p WHERE p.id = :id")
    Optional<PaymentRoute> findRoute(UUID id);

    @Query("SELECT p FROM EventPayment p WHERE p.id = :id AND p.eventId = :eventId")
    Optional<EventPayment> findByIdAndEvent(UUID id, UUID eventId);

    /** The guest's open checkout, if any (the unique index guarantees at most one). */
    @Query("SELECT p FROM EventPayment p WHERE p.guestId = :guestId AND p.status = 'PENDING'")
    Optional<EventPayment> findOpenForGuest(UUID guestId);

    @Query("SELECT p FROM EventPayment p WHERE p.guestId = :guestId AND p.status = 'PAID'")
    List<EventPayment> findPaidForGuest(UUID guestId);

    /** Payments still holding the payer's money (paid, or paid-but-unhonoured) for a guest — they follow a transferred ticket. */
    @Query("SELECT p FROM EventPayment p WHERE p.guestId = :guestId AND p.status IN ('PAID','REQUIRES_REFUND')")
    List<EventPayment> findMoneyHeldForGuest(UUID guestId);

    /** Every open checkout of an event — released when the event is cancelled. */
    @Query("SELECT p FROM EventPayment p WHERE p.eventId = :eventId AND p.status = 'PENDING'")
    List<EventPayment> findPendingByEvent(UUID eventId);

    @Query("SELECT p FROM EventPayment p WHERE p.eventId = :eventId " +
            "AND (:status IS NULL OR p.status = :status) ORDER BY p.createdAt DESC")
    Page<EventPayment> search(UUID eventId, String status, Pageable pageable);

    /** Open holds past their expiry — the sweep's work list, oldest first. */
    @Query("SELECT p.id FROM EventPayment p WHERE p.status = 'PENDING' AND p.expiresAt < :now ORDER BY p.expiresAt")
    List<UUID> findExpiredPendingIds(Instant now, Pageable pageable);

    /** Payments where money is held and some of it can still go back. */
    @Query("SELECT p FROM EventPayment p WHERE p.eventId = :eventId AND p.status IN ('PAID','REQUIRES_REFUND') " +
            "AND p.receivedAmount > p.refundedAmount ORDER BY p.paidAt")
    List<EventPayment> findRefundable(UUID eventId, Pageable pageable);

    // ── Aggregates for profitability and the close-out checklist ──────────────

    /** Money actually received (paid, or paid-but-unhonoured), before refunds. */
    @Query("SELECT SUM(p.receivedAmount) FROM EventPayment p WHERE p.eventId = :eventId " +
            "AND p.status IN ('PAID','REQUIRES_REFUND')")
    BigDecimal sumReceived(UUID eventId);

    @Query("SELECT SUM(p.refundedAmount) FROM EventPayment p WHERE p.eventId = :eventId")
    BigDecimal sumRefunded(UUID eventId);

    @Query("SELECT SUM(p.feeAmount) FROM EventPayment p WHERE p.eventId = :eventId " +
            "AND p.status IN ('PAID','REQUIRES_REFUND')")
    BigDecimal sumFees(UUID eventId);

    /** Value of checkouts still inside their hold — expected but not yet received. */
    @Query("SELECT SUM(p.amount) FROM EventPayment p WHERE p.eventId = :eventId AND p.status = 'PENDING' " +
            "AND p.expiresAt > :now")
    BigDecimal sumPendingAmount(UUID eventId, Instant now);

    /** What we owe back on payments flagged REQUIRES_REFUND. */
    @Query("SELECT SUM(p.receivedAmount - p.refundedAmount) FROM EventPayment p " +
            "WHERE p.eventId = :eventId AND p.status = 'REQUIRES_REFUND'")
    BigDecimal sumRefundsOwed(UUID eventId);

    @Query("SELECT COUNT(p) FROM EventPayment p WHERE p.eventId = :eventId AND p.status = 'PENDING'")
    long countPending(UUID eventId);

    @Query("SELECT COUNT(p) FROM EventPayment p WHERE p.eventId = :eventId AND p.status = 'REQUIRES_REFUND' " +
            "AND p.receivedAmount > p.refundedAmount")
    long countRefundsOwed(UUID eventId);
}