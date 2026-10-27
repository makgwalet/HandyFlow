package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.facilities.dto.ReorderSuggestionResponse;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What to reorder. An item at or below its reorder level is suggested unless open orders already cover the gap.
 * The suggested quantity is the item's standard reorder quantity, or — if larger — whatever brings on-hand plus
 * on-order back up to twice the reorder level, so one standard pack can never leave it still short.
 */
public final class FacilityReorderRules {

    private FacilityReorderRules() {}

    public static List<ReorderSuggestionResponse> suggest(List<FacilityStockItem> belowLevel, Map<UUID, BigDecimal> onOrderByItem) {
        return belowLevel.stream()
                .map(i -> {
                    BigDecimal onOrder = onOrderByItem.getOrDefault(i.getId(), BigDecimal.ZERO);
                    BigDecimal projected = i.getOnHand().add(onOrder);
                    if (projected.compareTo(i.getReorderLevel()) > 0) return null; // orders already cover it
                    BigDecimal target = i.getReorderLevel().multiply(BigDecimal.valueOf(2)).subtract(projected);
                    BigDecimal qty = i.getReorderQty().max(target);
                    return new ReorderSuggestionResponse(i.getId(), i.getSku(), i.getName(), i.getUnit(), i.getOnHand(),
                            i.getReorderLevel(), onOrder, qty, i.getUnitCost());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ReorderSuggestionResponse::name))
                .toList();
    }
}
