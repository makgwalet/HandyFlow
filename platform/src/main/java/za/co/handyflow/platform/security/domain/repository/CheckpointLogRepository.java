// security/domain/repository/CheckpointLogRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.CheckpointLog;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckpointLogRepository extends JpaRepository<CheckpointLog, UUID> {

    @Query("""
        SELECT l FROM CheckpointLog l
        WHERE l.tenantId = :tenantId
        AND l.checkpointId IN (
            SELECT c.id FROM Checkpoint c WHERE c.site.id = :siteId
        )
        ORDER BY l.scannedAt DESC
        """)
    Page<CheckpointLog> findBySite(TenantId tenantId, UUID siteId, Pageable pageable);

    @Query("""
        SELECT l FROM CheckpointLog l
        WHERE l.shiftId = :shiftId
        ORDER BY l.scannedAt ASC
        """)
    List<CheckpointLog> findByShift(UUID shiftId);

    /**
     * Count of checkpoint scans for a given shift.
     *
     * WHY? ShiftService.completeShift checks that at least
     * minScanCount scans were recorded before marking a shift COMPLETED.
     * This enforces proof-of-patrol at the shift level (fixes bug #17):
     * a guard can no longer close a shift with zero checkpoint scans.
     *
     * The count is intentionally NOT distinct on checkpoint_id — a patrol
     * round may legitimately scan the same checkpoint multiple times
     * (e.g. hourly patrol of the main gate over a 12-hour shift).
     */
    @Query("""
        SELECT COUNT(l) FROM CheckpointLog l
        WHERE l.shiftId = :shiftId
        """)
    long countByShiftId(UUID shiftId);

    /**
     * Most recent scan of a specific checkpoint within a specific shift.
     *
     * WHY? Cooldown enforcement (fixes bug #18):
     * the same checkpoint must not be scanned again within the configured
     * cooldown window.  The service fetches this and compares
     * scannedAt + cooldownSeconds against Instant.now().
     *
     * We return Optional<Instant> by fetching the log row and reading
     * scannedAt — JPA projections to Instant directly aren't
     * universally supported, so we return the full entity and the
     * service reads .getScannedAt().
     */
    @Query("""
        SELECT l FROM CheckpointLog l
        WHERE l.checkpointId = :checkpointId
        AND l.shiftId        = :shiftId
        ORDER BY l.scannedAt DESC
        LIMIT 1
        """)
    Optional<CheckpointLog> findLastScanInShift(UUID checkpointId, UUID shiftId);

    /**
     * Most recent scan of a checkpoint by any guard (across all shifts).
     * Used as a fallback cooldown check when a scan arrives without a shiftId.
     */
    @Query("""
        SELECT l FROM CheckpointLog l
        WHERE l.checkpointId = :checkpointId
        AND l.scannedAt >= :since
        ORDER BY l.scannedAt DESC
        LIMIT 1
        """)
    Optional<CheckpointLog> findLastScanSince(UUID checkpointId, Instant since);
}
