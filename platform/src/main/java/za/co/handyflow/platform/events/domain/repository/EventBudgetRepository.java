package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventBudget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventBudgetRepository extends JpaRepository<EventBudget, UUID> {

    /** Callers always resolve the event through a tenant-checked lookup first, so eventId alone is safe here. */
    @Query("SELECT b FROM EventBudget b WHERE b.eventId = :eventId")
    Optional<EventBudget> findByEvent(UUID eventId);

    /** Budget status for many events at once (events with no budget simply have no row). */
    @Query("SELECT b.eventId AS eventId, b.status AS label FROM EventBudget b WHERE b.eventId IN :eventIds")
    List<EventLabel> statusesForEvents(Collection<UUID> eventIds);
}