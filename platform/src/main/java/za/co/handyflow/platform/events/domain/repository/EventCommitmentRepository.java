package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventCommitment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventCommitmentRepository extends JpaRepository<EventCommitment, UUID> {

    @Query("SELECT c FROM EventCommitment c WHERE c.eventId = :eventId ORDER BY c.createdAt")
    List<EventCommitment> findByEvent(UUID eventId);

    @Query("SELECT c FROM EventCommitment c WHERE c.id = :id AND c.eventId = :eventId")
    Optional<EventCommitment> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT COUNT(c) FROM EventCommitment c WHERE c.budgetLineId = :lineId")
    long countByLine(UUID lineId);
}