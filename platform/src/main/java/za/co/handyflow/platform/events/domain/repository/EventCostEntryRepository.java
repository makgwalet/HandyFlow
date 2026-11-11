package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventCostEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventCostEntryRepository extends JpaRepository<EventCostEntry, UUID> {

    /** Every ACTIVE entry for the event — the input to the budget calculator. */
    @Query("SELECT e FROM EventCostEntry e WHERE e.eventId = :eventId AND e.status = 'ACTIVE'")
    List<EventCostEntry> findActiveByEvent(UUID eventId);

    @Query("SELECT e FROM EventCostEntry e WHERE e.id = :id AND e.eventId = :eventId")
    Optional<EventCostEntry> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT e FROM EventCostEntry e WHERE e.eventId = :eventId " +
            "AND (:commitmentId IS NULL OR e.commitmentId = :commitmentId) " +
            "AND (:lineId IS NULL OR e.budgetLineId = :lineId) " +
            "AND (:kind IS NULL OR e.kind = :kind) " +
            "AND (:status IS NULL OR e.status = :status) " +
            "ORDER BY e.entryDate DESC, e.createdAt DESC")
    Page<EventCostEntry> search(UUID eventId, UUID commitmentId, UUID lineId,
                                String kind, String status, Pageable pageable);

    @Query("SELECT COUNT(e) FROM EventCostEntry e WHERE e.budgetLineId = :lineId")
    long countByLine(UUID lineId);

    /** Any active invoice against the commitment — blocks cancelling it. */
    @Query("SELECT COUNT(e) > 0 FROM EventCostEntry e WHERE e.commitmentId = :commitmentId " +
            "AND e.kind = 'INVOICE' AND e.status = 'ACTIVE'")
    boolean existsActiveInvoiceForCommitment(UUID commitmentId);

    /** The same authoritative record linked twice. */
    @Query("SELECT COUNT(e) > 0 FROM EventCostEntry e WHERE e.eventId = :eventId AND e.status = 'ACTIVE' " +
            "AND e.sourceModule = :sourceModule AND e.sourceRef = :sourceRef")
    boolean existsActiveBySource(UUID eventId, String sourceModule, String sourceRef);

    /** The same invoice/payment typed in twice: same kind, amount and reference. */
    @Query("SELECT COUNT(e) > 0 FROM EventCostEntry e WHERE e.eventId = :eventId AND e.status = 'ACTIVE' " +
            "AND e.kind = :kind AND e.amount = :amount AND LOWER(e.reference) = LOWER(:reference)")
    boolean existsActiveDuplicate(UUID eventId, String kind, BigDecimal amount, String reference);
}