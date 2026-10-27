package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockLevel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityStockLevelRepository extends JpaRepository<FacilityStockLevel, UUID> {

    @Query("SELECT l FROM FacilityStockLevel l WHERE l.tenantId.value = :#{#tenantId.value} AND l.storeId = :storeId AND l.itemId = :itemId")
    Optional<FacilityStockLevel> findForStoreItem(@Param("tenantId") TenantId tenantId, @Param("storeId") UUID storeId, @Param("itemId") UUID itemId);

    @Query("SELECT l FROM FacilityStockLevel l WHERE l.tenantId.value = :#{#tenantId.value} AND l.itemId = :itemId")
    List<FacilityStockLevel> findForItem(@Param("tenantId") TenantId tenantId, @Param("itemId") UUID itemId);

    /** Every level above zero, for valuation and the store list. */
    @Query("SELECT l FROM FacilityStockLevel l WHERE l.tenantId.value = :#{#tenantId.value} AND l.onHand > 0")
    List<FacilityStockLevel> findAllPositive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT COUNT(l) FROM FacilityStockLevel l WHERE l.tenantId.value = :#{#tenantId.value} AND l.storeId = :storeId AND l.onHand > 0")
    long countPositiveInStore(@Param("tenantId") TenantId tenantId, @Param("storeId") UUID storeId);
}
