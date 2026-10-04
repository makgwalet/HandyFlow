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
import za.co.handyflow.platform.supplychain.application.SupplierFacade;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final SupplierFacade supplierFacade;

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovementsForItem(TenantId tenantId, UUID inventoryItemId, Pageable pageable) {
        Page<AgStockMovement> page = stockMovementRepository.findByInventoryItem(tenantId, inventoryItemId, pageable);
        Set<UUID> ids = page.getContent().stream().map(AgStockMovement::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) supplierFacade.findAll(tenantId, ids).forEach((k, v) -> names.put(k, v.name()));
        return page.map(m -> toResponse(m, names.get(m.getSupplierId())));
    }

    private StockMovementResponse toResponse(AgStockMovement m, String supplierName) {
        return new StockMovementResponse(
                m.getId(), m.getInventoryItemId(), m.getMovementType(), m.getMovementDate(), m.getQuantity(),
                m.getUnitCost(), m.getTotalCost(), m.getReferenceType(), m.getReferenceId(), m.getPerformedBy(),
                m.getPerformedByName(), m.getNotes(), m.getCreatedAt(), m.getSupplierId(), supplierName
        );
    }
}
