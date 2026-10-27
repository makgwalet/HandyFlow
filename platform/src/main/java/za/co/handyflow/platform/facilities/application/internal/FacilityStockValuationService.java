package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockLevel;
import za.co.handyflow.platform.facilities.domain.model.FacilityStore;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockItemRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockLevelRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStoreRepository;
import za.co.handyflow.platform.facilities.dto.StockValuationResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * The stock valuation report: on-hand stock at the item's current average cost, per store and in total. It reflects now;
 * a past date would need the ledger replayed and is not offered. Cost is sensitive, so the endpoints that use this are
 * manager views.
 */
@Service
@RequiredArgsConstructor
public class FacilityStockValuationService {

    private final FacilityStockLevelRepository levelRepository;
    private final FacilityStockItemRepository itemRepository;
    private final FacilityStoreRepository storeRepository;

    /** @param storeId limit to one store, or null for all */
    @Transactional(readOnly = true)
    public StockValuationResponse valuation(TenantId tenantId, UUID storeId) {
        Map<UUID, FacilityStore> stores = new LinkedHashMap<>();
        storeRepository.findAllForTenant(tenantId).forEach(s -> stores.put(s.getId(), s));
        if (storeId != null && !stores.containsKey(storeId)) throw new ResourceNotFoundException("FacilityStore", storeId.toString());

        List<FacilityStockLevel> levels = levelRepository.findAllPositive(tenantId).stream()
                .filter(l -> stores.containsKey(l.getStoreId()) && (storeId == null || l.getStoreId().equals(storeId))).toList();
        Map<UUID, FacilityStockItem> items = new HashMap<>();
        if (!levels.isEmpty())
            itemRepository.findByIds(tenantId, levels.stream().map(FacilityStockLevel::getItemId).distinct().toList()).forEach(i -> items.put(i.getId(), i));

        List<StockValuationResponse.Line> lines = new ArrayList<>();
        Map<UUID, BigDecimal> valueByStore = new HashMap<>();
        Map<UUID, Long> countByStore = new HashMap<>();
        for (FacilityStockLevel l : levels) {
            FacilityStockItem item = items.get(l.getItemId());
            if (item == null) continue;
            FacilityStore store = stores.get(l.getStoreId());
            BigDecimal value = l.getOnHand().multiply(item.getUnitCost()).setScale(2, RoundingMode.HALF_UP);
            lines.add(new StockValuationResponse.Line(store.getId(), store.getCode(), item.getId(), item.getSku(), item.getName(), item.getUnit(),
                    l.getOnHand(), item.getUnitCost(), value, item.isActive()));
            valueByStore.merge(store.getId(), value, BigDecimal::add);
            countByStore.merge(store.getId(), 1L, Long::sum);
        }
        lines.sort(Comparator.comparing(StockValuationResponse.Line::storeCode).thenComparing(StockValuationResponse.Line::sku));

        List<StockValuationResponse.StoreTotal> totals = new ArrayList<>();
        for (FacilityStore s : stores.values()) {
            if (storeId != null && !s.getId().equals(storeId)) continue;
            totals.add(new StockValuationResponse.StoreTotal(s.getId(), s.getCode(), s.getName(), countByStore.getOrDefault(s.getId(), 0L),
                    valueByStore.getOrDefault(s.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
        }
        BigDecimal total = totals.stream().map(StockValuationResponse.StoreTotal::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockValuationResponse(Instant.now(), total.setScale(2, RoundingMode.HALF_UP), totals, lines);
    }
}
