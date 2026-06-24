package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;

import java.util.List;
import java.util.UUID;

public interface ScPoLineRepository extends JpaRepository<ScPoLine, UUID> {

    @Query("SELECT l FROM ScPoLine l WHERE l.purchaseOrderId = :purchaseOrderId ORDER BY l.id")
    List<ScPoLine> findByPurchaseOrderId(UUID purchaseOrderId);

    @Query("SELECT COUNT(l) FROM ScPoLine l WHERE l.purchaseOrderId = :purchaseOrderId")
    int countByPurchaseOrderId(UUID purchaseOrderId);
}
