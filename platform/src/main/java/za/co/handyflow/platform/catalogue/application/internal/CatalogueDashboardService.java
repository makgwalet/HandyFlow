package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.CatalogueItemResponse;
import za.co.handyflow.platform.catalogue.dto.DashboardResponse;
import za.co.handyflow.platform.catalogue.dto.DashboardResponse.*;
import za.co.handyflow.platform.catalogue.dto.ItemOverviewResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Read-only views: the Catalogue dashboard and the overview of one item (Product 360). */
@Service
@RequiredArgsConstructor
public class CatalogueDashboardService {

    static final int GROWTH_MONTHS = 12, RECENT_ITEMS = 6, ACTIVITY_LINES = 10, RECENT_DAYS = 30;

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final CataloguePriceChangeRepository changeRepository;
    private final CatalogueStructureService structure;
    private final CatalogueService catalogueService;

    private Clock clock = Clock.system(CataloguePricingService.ZONE);

    void useClock(Clock clock) { this.clock = clock; }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(TenantId tenantId, Caller caller) {
        Instant now = clock.instant();
        var zone = CataloguePricingService.ZONE;

        long products = 0, services = 0, drafts = 0, suspended = 0, discontinued = 0, items = 0;
        for (Object[] row : itemRepository.countsByTypeAndStatus(tenantId)) {
            var type = (CatalogueItemType) row[0];
            var status = (CatalogueItemStatus) row[1];
            long n = (Long) row[2];
            if (status == CatalogueItemStatus.ARCHIVED) continue;
            items += n;
            if (CompletenessRules.isGoods(type)) products += n; else services += n;
            switch (status) {
                case DRAFT -> drafts += n;
                case SUSPENDED -> suspended += n;
                case DISCONTINUED -> discontinued += n;
                default -> { }
            }
        }
        var counts = new Counts(items, products, services, itemRepository.countLiveVariants(tenantId), itemRepository.countSellable(tenantId),
                drafts, suspended, discontinued,
                itemRepository.countDiscontinuedSince(tenantId, now.minus(java.time.Duration.ofDays(RECENT_DAYS))), itemRepository.countIncomplete(tenantId));

        PriceQueue prices = (caller.canSeePrices() || caller.canWritePrices() || caller.canApprovePrices())
                ? new PriceQueue(changeRepository.countByStatus(tenantId, PriceChangeStatus.PENDING), changeRepository.countByStatus(tenantId, PriceChangeStatus.SCHEDULED))
                : null;

        Instant since = DashboardRules.windowStart(now, zone, GROWTH_MONTHS);
        var growth = DashboardRules.monthlyGrowth(itemRepository.createdSince(tenantId, since), itemRepository.countCreatedBefore(tenantId, since), now, zone, GROWTH_MONTHS);

        Map<UUID, UUID> parents = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
        Map<UUID, Long> perCategory = new HashMap<>();
        long uncategorised = 0;
        for (Object[] row : itemRepository.countsByCategory(tenantId)) {
            if (row[0] == null) uncategorised += (Long) row[1]; else perCategory.put((UUID) row[0], (Long) row[1]);
        }
        var byCategory = DashboardRules.byRootCategory(parents, names, perCategory, uncategorised);

        var recent = itemRepository.recentlyChanged(tenantId, PageRequest.of(0, RECENT_ITEMS)).stream().map(i -> new RecentItem(
                i.getId(), i.getName(), i.getSku(), i.getCategory() == null ? null : i.getCategory().getName(), i.getStatus().name(),
                i.getDefaultPrice(), i.getUpdatedAt(), i.getParentId())).toList();

        var activity = auditRepository.recent(tenantId, PageRequest.of(0, ACTIVITY_LINES)).stream().map(e -> new ActivityLine(
                e.getAction().name(), HistoryRules.redact(e.getAction(), e.getDetail(), caller.canSeePrices()), e.getEntityType(), e.getEntityId(), e.getEntityName(), e.getCreatedAt())).toList();

        return new DashboardResponse(counts, prices, growth, byCategory, recent, activity);
    }

    @Transactional(readOnly = true)
    public ItemOverviewResponse overview(TenantId tenantId, UUID itemId, Caller caller) {
        var item = itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
        var result = CompletenessRules.evaluate(new CompletenessRules.Facts(item.getItemType(), item.getDescription(), item.getCategory() != null,
                item.getSku(), item.getBarcode(), item.getBrand(), item.getDefaultPrice()));
        UUID categoryId = item.getCategory() == null ? null : item.getCategory().getId();
        List<String> missing = item.isVariant() ? List.of() : structure.missingRequired(tenantId, categoryId, itemId);

        String path = null;
        if (categoryId != null) {
            Map<UUID, UUID> parents = new HashMap<>();
            Map<UUID, String> names = new HashMap<>();
            categoryRepository.findAllActive(tenantId).forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
            path = StructureRules.pathOf(categoryId, parents::get, names::get);
        }

        boolean mayKnowPrices = caller.canSeePrices() || caller.canWritePrices() || caller.canApprovePrices();
        var open = mayKnowPrices ? changeRepository.openForItem(tenantId, itemId) : List.<CataloguePriceChange>of();
        boolean pending = open.stream().anyMatch(c -> c.getStatus() == PriceChangeStatus.PENDING);
        String approval = pending ? "PRICE_CHANGE_PENDING" : item.getStatus() == CatalogueItemStatus.DRAFT ? "DRAFT" : "NONE";

        String parentName = item.isVariant() ? itemRepository.findActiveById(tenantId, item.getParentId()).map(CatalogueItem::getName).orElse(null) : null;
        CatalogueItemResponse response = catalogueService.getItem(tenantId, itemId, caller);
        return new ItemOverviewResponse(response, path,
                new ItemOverviewResponse.Completeness(result.score(), result.checks().stream().map(c -> new ItemOverviewResponse.CheckLine(c.code(), c.label(), c.passed())).toList()),
                missing, open.size(), approval, parentName);
    }
}
