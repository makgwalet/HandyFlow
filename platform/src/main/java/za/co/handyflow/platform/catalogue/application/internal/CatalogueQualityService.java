package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.catalogue.domain.model.DataQualityRules;
import za.co.handyflow.platform.catalogue.domain.model.DataQualityRules.Kind;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.DataQualityResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.util.*;

/** The data-quality report: runs the rules over every live item. Cost and minimum price findings go only to users who may see prices. */
@Service
@RequiredArgsConstructor
public class CatalogueQualityService {

    static final int PER_GROUP = 100;

    private final CatalogueItemRepository itemRepository;

    private Clock clock = Clock.system(CataloguePricingService.ZONE);

    void useClock(Clock clock) { this.clock = clock; }

    @Transactional(readOnly = true)
    public DataQualityResponse report(TenantId tenantId, Caller caller) {
        List<CatalogueItem> items = itemRepository.allLive(tenantId);
        Set<UUID> parents = new HashSet<>(itemRepository.parentIdsWithVariants(tenantId));
        Map<UUID, CatalogueItem> byId = new HashMap<>();
        List<DataQualityRules.Row> rows = new ArrayList<>();
        for (CatalogueItem i : items) {
            byId.put(i.getId(), i);
            rows.add(new DataQualityRules.Row(i.getId(), i.getName(), i.getSku(), i.getBarcode(), i.getBrand(), i.getDescription(), i.getCategory() != null, i.getItemType(),
                    i.getStatus(), i.getDefaultPrice(), i.getCostPrice(), i.getMinPrice(), i.isVariant(), parents.contains(i.getId()), i.getUpdatedAt()));
        }
        Map<Kind, List<DataQualityRules.Finding>> grouped = new EnumMap<>(Kind.class);
        for (var f : DataQualityRules.evaluate(rows, clock.instant())) {
            if (f.kind().needsPriceAccess && !caller.canSeePrices()) continue;
            grouped.computeIfAbsent(f.kind(), k -> new ArrayList<>()).add(f);
        }
        List<DataQualityResponse.Group> groups = new ArrayList<>();
        int total = 0;
        for (var e : grouped.entrySet()) {
            var list = e.getValue();
            total += list.size();
            var shown = list.stream().limit(PER_GROUP).map(f -> {
                var i = byId.get(f.itemId());
                return new DataQualityResponse.Finding(f.itemId(), i.getName(), i.getSku(), f.detail());
            }).sorted(Comparator.comparing(DataQualityResponse.Finding::name, String.CASE_INSENSITIVE_ORDER)).toList();
            groups.add(new DataQualityResponse.Group(e.getKey().name(), e.getKey().label, e.getKey().advice, list.size(), shown));
        }
        groups.sort(Comparator.comparingInt(DataQualityResponse.Group::count).reversed());
        return new DataQualityResponse(items.size(), total, groups);
    }
}
