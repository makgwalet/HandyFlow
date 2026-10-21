package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus;
import za.co.handyflow.platform.catalogue.domain.model.EnrichmentRules;
import za.co.handyflow.platform.catalogue.domain.model.EnrichmentRules.Candidate;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.ApplySuggestionRequest;
import za.co.handyflow.platform.catalogue.dto.CatalogueItemResponse;
import za.co.handyflow.platform.catalogue.dto.CreateItemRequest;
import za.co.handyflow.platform.catalogue.dto.EnrichmentResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Suggestions for filling gaps (category, brand, SKU, and a price hint), worked out from the tenant's own catalogue by
 * {@link EnrichmentRules}. Nothing here calls an outside service or learns from other tenants. A suggestion changes
 * nothing until a person applies it, and applying goes through the ordinary item edit, so its checks, duplicate rules and
 * history apply. Only a field that is still empty can be filled this way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueEnrichmentService {

    /** How many items the overview lists. */
    static final int MAX_ITEMS = 100;

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueService catalogueService;

    /** Items that have something we can suggest, those with the most confident suggestion first. */
    @Transactional(readOnly = true)
    public EnrichmentResponse overview(TenantId tenantId, Caller caller) {
        var all = itemRepository.allLive(tenantId);
        var index = new EnrichmentRules.Index(all.stream().map(CatalogueEnrichmentService::candidate).toList());
        List<EnrichmentResponse.Entry> found = new ArrayList<>();
        int scanned = 0;
        for (var i : all) {
            if (i.isVariant()) continue;   // variants follow their main item
            if (i.getStatus() == CatalogueItemStatus.ARCHIVED) continue;
            scanned++;
            var s = index.suggest(candidate(i), caller.canSeePrices());
            if (!s.isEmpty()) found.add(entry(i, s));
        }
        found.sort(Comparator.comparingInt((EnrichmentResponse.Entry e) -> -e.suggestions().stream().mapToInt(EnrichmentResponse.Suggestion::confidence).max().orElse(0))
                .thenComparing(EnrichmentResponse.Entry::name, String.CASE_INSENSITIVE_ORDER));
        boolean truncated = found.size() > MAX_ITEMS;
        return new EnrichmentResponse(scanned, found.size(), truncated, truncated ? found.subList(0, MAX_ITEMS) : found);
    }

    /** Suggestions for one item. */
    @Transactional(readOnly = true)
    public EnrichmentResponse.Entry forItem(TenantId tenantId, UUID itemId, Caller caller) {
        var item = itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
        if (item.isVariant()) return entry(item, List.of());
        var index = new EnrichmentRules.Index(itemRepository.allLive(tenantId).stream().map(CatalogueEnrichmentService::candidate).toList());
        return entry(item, index.suggest(candidate(item), caller.canSeePrices()));
    }

    /** Fills one empty field with what was suggested. The value is checked again here; it is never trusted from the request. */
    @Transactional
    public CatalogueItemResponse apply(TenantId tenantId, UUID itemId, ApplySuggestionRequest r, Caller caller) {
        if (r == null || r.kind() == null || r.value() == null || r.value().isBlank()) throw new IllegalArgumentException("Choose a suggestion to apply.");
        var item = itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
        if (item.isVariant()) throw new IllegalArgumentException("A variant follows its main item. Apply the suggestion to the main item.");
        if (item.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be edited.");
        String kind = r.kind().trim().toUpperCase(Locale.ROOT);
        String value = r.value().trim();
        UUID categoryId = item.getCategory() == null ? null : item.getCategory().getId();
        String brand = item.getBrand(), sku = item.getSku();
        switch (kind) {
            case EnrichmentRules.CATEGORY -> {
                if (categoryId != null) throw new IllegalArgumentException("This item already has a category.");
                UUID id;
                try { id = UUID.fromString(value); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("That category does not exist."); }
                categoryRepository.findActiveById(tenantId, id).orElseThrow(() -> new IllegalArgumentException("That category does not exist."));
                categoryId = id;
            }
            case EnrichmentRules.BRAND -> {
                if (brand != null && !brand.isBlank()) throw new IllegalArgumentException("This item already has a brand.");
                brand = value;
            }
            case EnrichmentRules.SKU -> {
                if (sku != null && !sku.isBlank()) throw new IllegalArgumentException("This item already has a SKU.");
                sku = value;
            }
            default -> throw new IllegalArgumentException("That kind of suggestion cannot be applied here.");
        }
        var out = catalogueService.updateItem(tenantId, itemId, new CreateItemRequest(item.getName(), item.getDescription(), categoryId, item.getUnit(),
                item.getDefaultPrice(), item.getVatRate(), sku, item.getBarcode(), brand, item.getItemType(), item.getCostPrice(),
                item.isVatExempt(), null, item.getVersion(), null), caller);
        log.info("Catalogue suggestion applied tenant={} item={} kind={}", tenantId, itemId, kind);
        return out;
    }

    private static Candidate candidate(CatalogueItem i) {
        return new Candidate(i.getId(), i.getName(), i.getCategory() == null ? null : i.getCategory().getId(), i.getCategory() == null ? null : i.getCategory().getName(),
                i.getBrand(), i.getSku(), i.getDefaultPrice(), i.getCostPrice());
    }

    private static EnrichmentResponse.Entry entry(CatalogueItem i, List<EnrichmentRules.Suggestion> s) {
        return new EnrichmentResponse.Entry(i.getId(), i.getName(), i.getSku(),
                s.stream().map(x -> new EnrichmentResponse.Suggestion(x.kind(), x.value(), x.label(), x.confidence(), x.reason(), x.applicable())).toList());
    }
}
