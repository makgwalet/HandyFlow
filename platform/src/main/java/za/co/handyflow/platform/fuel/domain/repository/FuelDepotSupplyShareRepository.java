package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDepotSupplyShare;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelDepotSupplyShareRepository extends JpaRepository<FuelDepotSupplyShare, UUID> {

    @Query("SELECT s FROM FuelDepotSupplyShare s WHERE s.tenantId = :tenantId ORDER BY s.depotId, s.percentage DESC")
    List<FuelDepotSupplyShare> findAllForTenant(TenantId tenantId);

    @Query("SELECT s FROM FuelDepotSupplyShare s WHERE s.tenantId = :tenantId AND s.depotId = :depotId ORDER BY s.percentage DESC")
    List<FuelDepotSupplyShare> findByDepot(TenantId tenantId, UUID depotId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelDepotSupplyShare s WHERE s.tenantId = :tenantId AND s.depotId = :depotId")
    void deleteByDepot(TenantId tenantId, UUID depotId);
}
