package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.pos.domain.model.PosStockAdjustmentItem;

import java.util.List;
import java.util.UUID;

public interface PosStockAdjustmentItemRepository extends JpaRepository<PosStockAdjustmentItem, UUID> {
    List<PosStockAdjustmentItem> findByAdjustmentId(UUID adjustmentId);
}
