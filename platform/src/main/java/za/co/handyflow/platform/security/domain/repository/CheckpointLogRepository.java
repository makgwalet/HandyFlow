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
import java.util.UUID;

public interface CheckpointLogRepository extends JpaRepository<CheckpointLog, UUID> {

    @Query("SELECT l FROM CheckpointLog l WHERE l.tenantId = :tenantId AND l.checkpointId IN (SELECT c.id FROM Checkpoint c WHERE c.site.id = :siteId) ORDER BY l.scannedAt DESC")
    Page<CheckpointLog> findBySite(TenantId tenantId, UUID siteId, Pageable pageable);

    @Query("SELECT l FROM CheckpointLog l WHERE l.shiftId = :shiftId ORDER BY l.scannedAt ASC")
    List<CheckpointLog> findByShift(UUID shiftId);
}