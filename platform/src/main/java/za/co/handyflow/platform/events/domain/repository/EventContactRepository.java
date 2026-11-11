package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventContactRepository extends JpaRepository<EventContact, UUID> {

    @Query("SELECT c FROM EventContact c WHERE c.eventId = :eventId ORDER BY c.contactType, c.name")
    List<EventContact> findByEvent(UUID eventId);

    @Query("SELECT c FROM EventContact c WHERE c.id = :id AND c.eventId = :eventId")
    Optional<EventContact> findByIdAndEvent(UUID id, UUID eventId);
}