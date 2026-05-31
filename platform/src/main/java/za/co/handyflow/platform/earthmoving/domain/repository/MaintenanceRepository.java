// earthmoving/domain/repository/MaintenanceRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.MaintenanceRecord;

import java.util.UUID;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, UUID> {

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.assetId = :assetId AND m.deletedAt IS NULL ORDER BY m.performedAt DESC")
    Page<MaintenanceRecord> findByAsset(UUID assetId, Pageable pageable);
}