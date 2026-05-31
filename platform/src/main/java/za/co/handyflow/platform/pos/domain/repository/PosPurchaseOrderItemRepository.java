package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.pos.domain.model.PosPurchaseOrderItem;

import java.util.List;
import java.util.UUID;

public interface PosPurchaseOrderItemRepository extends JpaRepository<PosPurchaseOrderItem, UUID> {
    List<PosPurchaseOrderItem> findByPurchaseOrderId(UUID purchaseOrderId);
}
