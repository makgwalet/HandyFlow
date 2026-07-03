// earthmoving/domain/repository/OperatorLogRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.OperatorLog;

import java.util.Optional;
import java.util.UUID;

public interface OperatorLogRepository extends JpaRepository<OperatorLog, UUID> {

    @Query("SELECT l FROM OperatorLog l WHERE l.assetId = :assetId ORDER BY l.startedAt DESC")
    Page<OperatorLog> findByAsset(UUID assetId, Pageable pageable);

    // Needed to complete a shift: look the log up scoped to its asset so a
    // caller can't complete a log belonging to a different asset by guessing
    // an id (defence in depth alongside the tenant check already done on the
    // asset itself in the service layer).
    @Query("SELECT l FROM OperatorLog l WHERE l.id = :id AND l.assetId = :assetId")
    Optional<OperatorLog> findByIdAndAssetId(UUID id, UUID assetId);

    // Used to stop a second shift being started on a machine that's already
    // mid-shift (endedAt IS NULL). The original code let you call startLog
    // any number of times in a row with no such check.
    @Query("SELECT l FROM OperatorLog l WHERE l.assetId = :assetId AND l.endedAt IS NULL")
    Optional<OperatorLog> findOpenLogForAsset(UUID assetId);
}