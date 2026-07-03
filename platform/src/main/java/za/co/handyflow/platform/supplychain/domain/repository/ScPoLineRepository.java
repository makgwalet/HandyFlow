package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScPoLineRepository extends JpaRepository<ScPoLine, UUID> {

    // WHY @Param("purchaseOrderId") on the method parameter?
    // Spring Data JPA can infer parameter names from method argument names
    // only when the -parameters compiler flag is set. Without it, the binding
    // is null and the query returns everything. Explicit @Param is always safe.
    @Query("SELECT l FROM ScPoLine l WHERE l.purchaseOrderId = :purchaseOrderId ORDER BY l.id")
    List<ScPoLine> findByPurchaseOrderId(@Param("purchaseOrderId") UUID purchaseOrderId);

    @Query("SELECT l FROM ScPoLine l WHERE l.id = :id AND l.tenantId = :tenantId")
    Optional<ScPoLine> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(l) FROM ScPoLine l WHERE l.purchaseOrderId = :purchaseOrderId")
    int countByPurchaseOrderId(@Param("purchaseOrderId") UUID purchaseOrderId);

    /**
     * Find a PO line by catalogueItemId within a specific PO.
     * Used in postGoodsReceipt to locate which line to credit the received quantity to.
     * Returns first match — a PO should not have duplicate catalogue items, but if it
     * does, the first line (by insertion order) is updated.
     */
    @Query("SELECT l FROM ScPoLine l WHERE l.purchaseOrderId = :poId AND l.catalogueItemId = :itemId ORDER BY l.id")
    List<ScPoLine> findByPoAndCatalogueItem(@Param("poId") UUID purchaseOrderId,
                                            @Param("itemId") UUID catalogueItemId);

    /**
     * Check whether all lines on a PO are fully received.
     * Used after posting a GR to decide whether to call po.fullyReceive().
     *
     * WHY a COUNT query instead of loading all lines?
     * Loading 50 PO lines to check if isFullyReceived == true on each is wasteful.
     * COUNT(lines where NOT fully received) == 0 means all are done. One integer returned.
     */
    @Query("SELECT COUNT(l) FROM ScPoLine l WHERE l.purchaseOrderId = :poId AND l.isFullyReceived = false")
    long countNotFullyReceived(@Param("poId") UUID purchaseOrderId);
}