package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventChecklistItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventChecklistItemRepository extends JpaRepository<EventChecklistItem, UUID> {

    /** Phase order is applied in the service (it isn't alphabetical); within a phase, by sort order then age. */
    @Query("SELECT c FROM EventChecklistItem c WHERE c.eventId = :eventId ORDER BY c.sortOrder, c.createdAt")
    List<EventChecklistItem> findByEvent(UUID eventId);

    @Query("SELECT c FROM EventChecklistItem c WHERE c.id = :id AND c.eventId = :eventId")
    Optional<EventChecklistItem> findByIdAndEvent(UUID id, UUID eventId);

    /** Highest sort order in a phase, so new items go to the end. Null when the phase is empty. */
    @Query("SELECT MAX(c.sortOrder) FROM EventChecklistItem c WHERE c.eventId = :eventId AND c.phase = :phase")
    Integer maxSortOrder(UUID eventId, String phase);
}