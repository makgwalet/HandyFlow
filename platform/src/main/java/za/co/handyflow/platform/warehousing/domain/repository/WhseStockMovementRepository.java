package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseStockMovement;

import java.util.List;
import java.util.UUID;

public interface WhseStockMovementRepository extends JpaRepository<WhseStockMovement, UUID> {

    /** Full movement history for one item at one location — the audit trail behind a WhseInventory position. */
    @Query("SELECT m FROM WhseStockMovement m WHERE m.tenantId = :tenantId AND m.clientId = :clientId AND m.itemId = :itemId AND m.locationId = :locationId ORDER BY m.createdAt DESC")
    List<WhseStockMovement> findByPosition(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                            @Param("itemId") UUID itemId, @Param("locationId") UUID locationId);

    @Query("SELECT m FROM WhseStockMovement m WHERE m.tenantId = :tenantId AND m.clientId = :clientId ORDER BY m.createdAt DESC")
    Page<WhseStockMovement> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT m FROM WhseStockMovement m WHERE m.tenantId = :tenantId AND m.referenceType = :referenceType AND m.referenceId = :referenceId ORDER BY m.createdAt ASC")
    List<WhseStockMovement> findByReference(@Param("tenantId") UUID tenantId, @Param("referenceType") String referenceType, @Param("referenceId") UUID referenceId);
}
