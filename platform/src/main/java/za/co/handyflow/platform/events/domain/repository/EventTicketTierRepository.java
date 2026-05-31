package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTicketTierRepository extends JpaRepository<EventTicketTier, UUID> {

    @Query("SELECT t FROM EventTicketTier t WHERE t.eventId = :eventId AND t.active = true ORDER BY t.price")
    List<EventTicketTier> findByEvent(UUID eventId);

    @Query("SELECT t FROM EventTicketTier t WHERE t.id = :id AND t.eventId = :eventId")
    Optional<EventTicketTier> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT COUNT(t) FROM EventTicketTier t WHERE t.eventId = :eventId AND t.active = true AND t.quantitySold < t.quantity")
    long countAvailableTiers(UUID eventId);
}