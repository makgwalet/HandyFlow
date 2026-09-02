package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.StockMovementResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Read-only: every stock movement in this Increment is written as a
 * follow-through of an {@link AgInventoryItemService} receive/issue/adjust
 * call or an {@link AgFeedRecordService} feed-with-inventory-item call — see
 * {@link AgStockMovement}'s own Javadoc on {@code referenceType}/
 * {@code referenceId}. This service exists so the history is independently
 * browsable per item, without a write path of its own in Increment 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgStockMovementService {

    private final AgStockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovementsForItem(TenantId tenantId, UUID inventoryItemId, Pageable pageable) {
        return stockMovementRepository.findByInventoryItem(tenantId, inventoryItemId, pageable).map(this::toResponse);
    }

    private StockMovementResponse toResponse(AgStockMovement m) {
        return new StockMovementResponse(
                m.getId(), m.getInventoryItemId(), m.getMovementType(), m.getMovementDate(), m.getQuantity(),
                m.getUnitCost(), m.getTotalCost(), m.getReferenceType(), m.getReferenceId(), m.getPerformedBy(),
                m.getPerformedByName(), m.getNotes(), m.getCreatedAt()
        );
    }
}
