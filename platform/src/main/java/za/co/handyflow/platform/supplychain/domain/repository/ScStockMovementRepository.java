package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScStockMovement;

import java.util.List;
import java.util.UUID;

public interface ScStockMovementRepository extends JpaRepository<ScStockMovement, UUID> {

    @Query("SELECT m FROM ScStockMovement m WHERE m.inventoryId = :inventoryId ORDER BY m.createdAt DESC")
    Page<ScStockMovement> findByInventoryId(@Param("inventoryId") UUID inventoryId, Pageable pageable);

    @Query("SELECT m FROM ScStockMovement m WHERE m.referenceType = :refType AND m.referenceId = :refId")
    List<ScStockMovement> findByReference(@Param("refType") String referenceType, @Param("refId") UUID referenceId);
}
