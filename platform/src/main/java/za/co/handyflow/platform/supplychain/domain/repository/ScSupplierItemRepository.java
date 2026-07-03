package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierItemRepository extends JpaRepository<ScSupplierItem, UUID> {

    @Query("SELECT i FROM ScSupplierItem i WHERE i.supplierId = :supplierId ORDER BY i.itemName")
    List<ScSupplierItem> findBySupplierId(@Param("supplierId") UUID supplierId);

    /**
     * Best-price lookup: given a catalogue item, which suppliers stock it
     * and at what price? Ordered cheapest-first so the UI can highlight the
     * best option when a buyer creates a PO.
     */
    @Query("SELECT i FROM ScSupplierItem i WHERE i.tenantId = :tenantId AND i.catalogueItemId = :itemId ORDER BY i.unitCost ASC")
    List<ScSupplierItem> findByCatalogueItemOrderedByPrice(@Param("tenantId") UUID tenantId,
                                                           @Param("itemId") UUID catalogueItemId);

    @Query("SELECT i FROM ScSupplierItem i WHERE i.supplierId = :supplierId AND i.catalogueItemId = :itemId")
    Optional<ScSupplierItem> findBySupplierAndCatalogueItem(@Param("supplierId") UUID supplierId,
                                                            @Param("itemId") UUID catalogueItemId);
}