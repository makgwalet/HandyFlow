// earthmoving/domain/repository/MaintenanceRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.MaintenanceRecord;

import java.util.UUID;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, UUID> {

    /** Costed, not-deleted service records for the whole company in a time window (from inclusive, to exclusive). */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.tenantId = :tenantId AND m.deletedAt IS NULL AND m.cost IS NOT NULL "
            + "AND m.performedAt >= :from AND m.performedAt < :to")
    java.util.List<MaintenanceRecord> findCostedBetween(za.co.handyflow.platform.shared.TenantId tenantId, java.time.Instant from, java.time.Instant to);

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.assetId = :assetId AND m.deletedAt IS NULL ORDER BY m.performedAt DESC")
    Page<MaintenanceRecord> findByAsset(UUID assetId, Pageable pageable);
}