package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelRouteCostEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelRouteCostEntryRepository extends JpaRepository<FuelRouteCostEntry, UUID> {

    @Query("SELECT e FROM FuelRouteCostEntry e WHERE e.tenantId = :tenantId AND e.routeId = :routeId ORDER BY e.recordedAt")
    List<FuelRouteCostEntry> findByRoute(TenantId tenantId, UUID routeId);

    @Query("SELECT e FROM FuelRouteCostEntry e WHERE e.tenantId = :tenantId AND e.routeId IN :routeIds AND e.status = 'ACTIVE'")
    List<FuelRouteCostEntry> findActiveByRoutes(TenantId tenantId, Collection<UUID> routeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FuelRouteCostEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<FuelRouteCostEntry> findByIdForUpdate(TenantId tenantId, UUID id);
}
