package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAlert;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthAlertRepository extends JpaRepository<EarthAlert, UUID> {
    @Query("SELECT a FROM EarthAlert a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<EarthAlert> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT a FROM EarthAlert a WHERE a.tenantId = :tenantId AND a.dedupeKey = :key AND a.status <> 'RESOLVED'")
    Optional<EarthAlert> findLiveByKey(TenantId tenantId, String key);

    @Query("SELECT a FROM EarthAlert a WHERE a.tenantId = :tenantId AND (:openOnly = false OR a.status <> 'RESOLVED') ORDER BY a.createdAt DESC")
    Page<EarthAlert> findForTenant(TenantId tenantId, boolean openOnly, Pageable pageable);

    @Query("SELECT a FROM EarthAlert a WHERE a.tenantId = :tenantId AND a.status = 'OPEN' AND a.level = 1")
    List<EarthAlert> findEscalationCandidates(TenantId tenantId);

    @Query("SELECT DISTINCT a.tenantId.value FROM EarthAlert a WHERE a.status = 'OPEN' AND a.level = 1")
    List<UUID> findTenantIdsWithEscalationCandidates();
}
