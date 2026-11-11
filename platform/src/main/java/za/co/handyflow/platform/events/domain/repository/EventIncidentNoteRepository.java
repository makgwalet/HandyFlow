package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventIncidentNote;

import java.util.List;
import java.util.UUID;

public interface EventIncidentNoteRepository extends JpaRepository<EventIncidentNote, UUID> {

    @Query("SELECT n FROM EventIncidentNote n WHERE n.incidentId = :incidentId ORDER BY n.createdAt ASC")
    List<EventIncidentNote> findTimeline(UUID incidentId);
}