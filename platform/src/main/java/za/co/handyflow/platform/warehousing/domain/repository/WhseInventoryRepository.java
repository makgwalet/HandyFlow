package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseInventoryRepository extends JpaRepository<WhseInventory, UUID> {

    /** The unique (tenantId, clientId, itemId, locationId) position — enforced at the DB level, see V258 migration. */
    @Query("SELECT i FROM WhseInventory i WHERE i.tenantId = :tenantId AND i.clientId = :clientId AND i.itemId = :itemId AND i.locationId = :locationId")
    Optional<WhseInventory> findPosition(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                          @Param("itemId") UUID itemId, @Param("locationId") UUID locationId);

    /** Every location holding stock for one item — used to pick a location to allocate/fulfil from. */
    @Query("SELECT i FROM WhseInventory i WHERE i.tenantId = :tenantId AND i.clientId = :clientId AND i.itemId = :itemId ORDER BY i.qtyOnHand DESC")
    List<WhseInventory> findByClientAndItem(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, @Param("itemId") UUID itemId);

    /** Full stock position for a client across every item/location — used for the billing storage snapshot and the client portal inventory view. */
    @Query("SELECT i FROM WhseInventory i WHERE i.tenantId = :tenantId AND i.clientId = :clientId")
    List<WhseInventory> findAllForClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT i FROM WhseInventory i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<WhseInventory> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
