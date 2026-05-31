// earthmoving/domain/repository/OperatorLogRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.OperatorLog;

import java.util.UUID;

public interface OperatorLogRepository extends JpaRepository<OperatorLog, UUID> {

    @Query("SELECT l FROM OperatorLog l WHERE l.assetId = :assetId ORDER BY l.startedAt DESC")
    Page<OperatorLog> findByAsset(UUID assetId, Pageable pageable);
}