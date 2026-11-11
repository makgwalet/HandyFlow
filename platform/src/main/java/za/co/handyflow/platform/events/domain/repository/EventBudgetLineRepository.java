package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventBudgetLine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventBudgetLineRepository extends JpaRepository<EventBudgetLine, UUID> {

    @Query("SELECT l FROM EventBudgetLine l WHERE l.eventId = :eventId ORDER BY l.category, l.createdAt")
    List<EventBudgetLine> findByEvent(UUID eventId);

    @Query("SELECT l FROM EventBudgetLine l WHERE l.id = :id AND l.eventId = :eventId")
    Optional<EventBudgetLine> findByIdAndEvent(UUID id, UUID eventId);
}