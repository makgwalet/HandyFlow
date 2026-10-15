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

    /** Newest events that put the machine into the given status; used to find when it went out of service. */
    @Query("SELECT e FROM EarthStatusEvent e WHERE e.tenantId = :tenantId AND e.assetId = :assetId AND e.toStatus = :toStatus ORDER BY e.happenedAt DESC")
    java.util.List<EarthStatusEvent> findLatestInto(TenantId tenantId, UUID assetId, String toStatus, Pageable pageable);

    /** Every status change of the tenant before {@code to}, grouped by machine and oldest first. Used for the downtime report. */
    @Query("SELECT e FROM EarthStatusEvent e WHERE e.tenantId = :tenantId AND e.happenedAt < :to ORDER BY e.assetId, e.happenedAt")
    java.util.List<EarthStatusEvent> findUpTo(TenantId tenantId, java.time.Instant to);
}
