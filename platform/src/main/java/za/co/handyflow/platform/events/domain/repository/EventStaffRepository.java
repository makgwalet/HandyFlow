package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventStaffAssignment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventStaffRepository extends JpaRepository<EventStaffAssignment, UUID> {

    @Query("SELECT a FROM EventStaffAssignment a WHERE a.eventId = :eventId ORDER BY a.role, a.shiftStart, a.fullName")
    List<EventStaffAssignment> findByEvent(UUID eventId);

    @Query("SELECT a FROM EventStaffAssignment a WHERE a.id = :id AND a.eventId = :eventId")
    Optional<EventStaffAssignment> findByIdAndEvent(UUID id, UUID eventId);

    /**
     * Other live shifts for the same platform user that overlap [start, end) — in ANY event of the tenant,
     * which is the point: the same person can't be on two stages at once. Touching shifts (one ends exactly
     * when the next starts) don't overlap. excludeId is never null — pass a random UUID when creating.
     */
    @Query("SELECT a FROM EventStaffAssignment a WHERE a.tenantId = :tenantId AND a.userId = :userId " +
            "AND a.status IN ('ASSIGNED','CONFIRMED','ARRIVED') AND a.id <> :excludeId " +
            "AND a.shiftStart < :end AND a.shiftEnd > :start")
    List<EventStaffAssignment> findClashes(UUID tenantId, UUID userId, LocalDateTime start,
                                           LocalDateTime end, UUID excludeId);
}