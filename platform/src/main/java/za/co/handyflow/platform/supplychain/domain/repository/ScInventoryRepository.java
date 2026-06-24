package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScInventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScInventoryRepository extends JpaRepository<ScInventory, UUID> {

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId")
    List<ScInventory> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.locationId = :locationId")
    List<ScInventory> findByTenantIdAndLocation(@Param("tenantId") UUID tenantId,
                                                @Param("locationId") UUID locationId);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.locationId = :locationId AND i.catalogueItemId = :itemId")
    Optional<ScInventory> findByTenantIdAndLocationIdAndCatalogueItemId(
            @Param("tenantId") UUID tenantId,
            @Param("locationId") UUID locationId,
            @Param("itemId") UUID catalogueItemId);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.reorderPoint > 0 AND i.qtyOnHand <= i.reorderPoint")
    List<ScInventory> findLowStock(@Param("tenantId") UUID tenantId);
}
