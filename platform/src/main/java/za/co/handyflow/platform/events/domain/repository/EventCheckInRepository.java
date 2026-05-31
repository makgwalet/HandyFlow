package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventCheckIn;

import java.util.List;
import java.util.UUID;

public interface EventCheckInRepository extends JpaRepository<EventCheckIn, UUID> {

    @Query("SELECT c FROM EventCheckIn c WHERE c.eventId = :eventId ORDER BY c.scannedAt DESC")
    List<EventCheckIn> findByEvent(UUID eventId);

    @Query("SELECT COUNT(c) FROM EventCheckIn c WHERE c.eventId = :eventId AND c.result = 'SUCCESS'")
    long countSuccessful(UUID eventId);
}