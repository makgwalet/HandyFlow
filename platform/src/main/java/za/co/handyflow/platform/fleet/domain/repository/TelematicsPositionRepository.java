package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TelematicsPosition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read side only; positions are inserted in bulk by TelematicsIngestService. */
public interface TelematicsPositionRepository extends JpaRepository<TelematicsPosition, UUID> {

    @Query("SELECT p FROM TelematicsPosition p WHERE p.vehicleId = :vehicleId AND p.recordedAt >= :from " +
            "AND p.recordedAt <= :to ORDER BY p.recordedAt ASC")
    List<TelematicsPosition> findTrack(UUID vehicleId, Instant from, Instant to);
}