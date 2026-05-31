package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventGuest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventGuestRepository extends JpaRepository<EventGuest, UUID> {

    @Query("""
        SELECT g FROM EventGuest g WHERE g.eventId = :eventId
        AND (:status IS NULL OR g.status = :status)
        AND (:tierId IS NULL OR g.tierId = :tierId)
        ORDER BY g.createdAt DESC
        """)
    Page<EventGuest> findByEvent(UUID eventId, String status,
                                 UUID tierId, Pageable pageable);

    @Query("SELECT g FROM EventGuest g WHERE g.qrCode = :qrCode")
    Optional<EventGuest> findByQrCode(String qrCode);

    @Query("SELECT g FROM EventGuest g WHERE g.id = :id AND g.eventId = :eventId")
    Optional<EventGuest> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId AND g.status NOT IN ('CANCELLED','NO_SHOW')")
    long countActive(UUID eventId);

    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId AND g.status = 'CHECKED_IN'")
    long countCheckedIn(UUID eventId);

    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId AND g.status = 'CHECKED_IN'")
    List<EventGuest> findCheckedIn(UUID eventId);
}