package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventRefund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRefundRepository extends JpaRepository<EventRefund, UUID> {

    @Query("SELECT r FROM EventRefund r WHERE r.id = :id AND r.eventId = :eventId")
    Optional<EventRefund> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT r FROM EventRefund r WHERE r.eventId = :eventId ORDER BY r.requestedAt DESC")
    List<EventRefund> findByEvent(UUID eventId);

    /** Amount already promised back but not yet completed — counted against what is still refundable. */
    @Query("SELECT SUM(r.amount) FROM EventRefund r WHERE r.paymentId = :paymentId AND r.status = 'REQUESTED'")
    BigDecimal sumRequestedForPayment(UUID paymentId);

    @Query("SELECT COUNT(r) FROM EventRefund r WHERE r.eventId = :eventId AND r.status = 'REQUESTED'")
    long countRequested(UUID eventId);
}