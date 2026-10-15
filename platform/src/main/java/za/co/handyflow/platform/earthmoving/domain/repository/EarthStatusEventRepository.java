package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthStatusEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface EarthStatusEventRepository extends JpaRepository<EarthStatusEvent, UUID> {

    @Query("SELECT e FROM EarthStatusEvent e WHERE e.tenantId = :tenantId AND e.assetId = :assetId ORDER BY e.happenedAt DESC")
    Page<EarthStatusEvent> findByAsset(TenantId tenantId, UUID assetId, Pageable pageable);
}
