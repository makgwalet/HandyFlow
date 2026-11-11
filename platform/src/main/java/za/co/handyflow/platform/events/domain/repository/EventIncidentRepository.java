package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventIncident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventIncidentRepository extends JpaRepository<EventIncident, UUID> {

    @Query("SELECT i FROM EventIncident i WHERE i.id = :id AND i.eventId = :eventId")
    Optional<EventIncident> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT i FROM EventIncident i WHERE i.eventId = :eventId " +
            "AND (:activeOnly = false OR i.status IN ('OPEN','IN_PROGRESS','ESCALATED')) " +
            "AND (:severity IS NULL OR i.severity = :severity) ORDER BY i.createdAt DESC")
    Page<EventIncident> search(UUID eventId, boolean activeOnly, String severity, Pageable pageable);

    @Query("SELECT i FROM EventIncident i WHERE i.eventId = :eventId AND i.status IN ('OPEN','IN_PROGRESS','ESCALATED')")
    List<EventIncident> findUnresolved(UUID eventId);

    @Query("SELECT COUNT(i) FROM EventIncident i WHERE i.eventId = :eventId")
    long countByEvent(UUID eventId);
}