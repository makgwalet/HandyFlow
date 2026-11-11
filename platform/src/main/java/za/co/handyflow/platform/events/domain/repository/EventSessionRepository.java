package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSession, UUID> {

    @Query("SELECT s FROM EventSession s WHERE s.eventId = :eventId ORDER BY s.startAt, s.room")
    List<EventSession> findByEvent(UUID eventId);

    @Query("SELECT s FROM EventSession s WHERE s.id = :id AND s.eventId = :eventId")
    Optional<EventSession> findByIdAndEvent(UUID id, UUID eventId);

    /** Another session in this room is running right now. excludeId is never null — pass a random UUID when there is none. */
    @Query("SELECT COUNT(s) > 0 FROM EventSession s WHERE s.eventId = :eventId AND s.status = 'IN_PROGRESS' " +
            "AND LOWER(s.room) = LOWER(:room) AND s.id <> :excludeId")
    boolean existsInProgressInRoom(UUID eventId, String room, UUID excludeId);
}