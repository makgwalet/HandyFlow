package za.co.handyflow.platform.facilities.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityStockItemRepository extends JpaRepository<FacilityStockItem, UUID> {

    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.id = :id")
    Optional<FacilityStockItem> findForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Row-locked read: every change to quantity on hand goes through this so concurrent issues cannot oversell. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.id = :id")
    Optional<FacilityStockItem> findForUpdate(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT COUNT(i) > 0 FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.sku = :sku")
    boolean skuExists(@Param("tenantId") TenantId tenantId, @Param("sku") String sku);

    /** {@code pattern} is a lower-cased LIKE pattern such as {@code %pump%}; {@code %} matches everything. */
    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.active = :active "
            + "AND (LOWER(i.name) LIKE :pattern OR LOWER(i.sku) LIKE :pattern)")
    Page<FacilityStockItem> search(@Param("tenantId") TenantId tenantId, @Param("active") boolean active,
                                   @Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.active = true "
            + "AND i.reorderLevel > 0 AND i.onHand <= i.reorderLevel ORDER BY i.name")
    List<FacilityStockItem> findBelowReorderLevel(@Param("tenantId") TenantId tenantId);

    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.active = true")
    List<FacilityStockItem> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT i FROM FacilityStockItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.id IN :ids")
    List<FacilityStockItem> findByIds(@Param("tenantId") TenantId tenantId, @Param("ids") Collection<UUID> ids);
}
