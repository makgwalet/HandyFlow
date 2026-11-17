package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopTabLineRepository extends JpaRepository<FuelTruckStopTabLine, UUID> {

    @Query("SELECT l FROM FuelTruckStopTabLine l WHERE l.tenantId = :tenantId AND l.tabId = :tabId ORDER BY l.addedAt")
    List<FuelTruckStopTabLine> findForTab(TenantId tenantId, UUID tabId);

    @Query("SELECT l FROM FuelTruckStopTabLine l WHERE l.tenantId = :tenantId AND l.tabId IN :tabIds ORDER BY l.addedAt")
    List<FuelTruckStopTabLine> findForTabs(TenantId tenantId, Collection<UUID> tabIds);

    @Query("SELECT l FROM FuelTruckStopTabLine l WHERE l.tenantId = :tenantId AND l.tabId = :tabId AND l.id = :id")
    Optional<FuelTruckStopTabLine> findOwned(TenantId tenantId, UUID tabId, UUID id);
}
