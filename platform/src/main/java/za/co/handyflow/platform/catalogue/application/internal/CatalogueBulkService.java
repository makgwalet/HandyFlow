package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

/**
 * One change to many items: status, category, or prices by a percentage. Every item goes through the same service call a
 * single edit would, one at a time, so permissions, history and approval rules all apply and one refusal never stops the others.
 * The dry run checks the rules it can see without saving; a few final checks (required attributes, for example) only run when applied.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueBulkService {

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueService catalogueService;
    private final CataloguePricingService pricing;

    public BulkResponse run(TenantId tenantId, BulkRequest r, Caller caller) {
        List<UUID> ids = r.ids().stream().distinct().toList();
        BulkRules.checkCount(ids.size());
        String action = r.action().trim().toUpperCase(Locale.ROOT);
        BigDecimal percent = null;
        UUID listId = null;
        String category = null;
        switch (action) {
            case "STATUS" -> {
                if (r.status() == null) throw new IllegalArgumentException("Choose the status to move the items to.");
                if (r.status().needsArchivePermission() && !caller.canArchive()) throw new AccessDeniedException("You are not allowed to discontinue or archive items.");
            }
            case "CATEGORY" -> {
                if (r.categoryId() == null) throw new IllegalArgumentException("Choose the category to move the items to.");
                var c = categoryRepository.findActiveById(tenantId, r.categoryId()).orElseThrow(() -> new IllegalArgumentException("That category does not exist."));
                category = c.getName();
            }
            case "PRICE_PERCENT" -> {
                if (!caller.canWritePrices()) throw new AccessDeniedException("You are not allowed to change prices.");
                percent = BulkRules.checkPercent(r.percent());
                listId = pricing.defaultListId(tenantId);
            }
            default -> throw new IllegalArgumentException("Unknown change '" + r.action() + "'.");
        }
        Map<UUID, CatalogueItem> found = new HashMap<>();
        itemRepository.findLiveByIds(tenantId, ids).forEach(i -> found.put(i.getId(), i));
        Set<UUID> parents = new HashSet<>(itemRepository.parentIdsWithVariants(tenantId));

        List<BulkResponse.Row> rows = new ArrayList<>();
        int ok = 0, unchanged = 0, refused = 0;
        for (UUID id : ids) {
            CatalogueItem item = found.get(id);
            if (item == null) { refused++; rows.add(new BulkResponse.Row(id, "(not found)", null, "REFUSED", "This item no longer exists.")); continue; }
            String outcome, message;
            try {
                String[] res = switch (action) {
                    case "STATUS" -> status(tenantId, item, r, caller);
                    case "CATEGORY" -> category(tenantId, item, r, caller, category);
                    default -> price(tenantId, item, parents.contains(id), percent, listId, r, caller);
                };
                outcome = res[0]; message = res[1];
            } catch (RuntimeException e) {
                outcome = "REFUSED"; message = e.getMessage() == null ? "Could not be changed." : e.getMessage();
            }
            switch (outcome) { case "OK" -> ok++; case "UNCHANGED" -> unchanged++; default -> refused++; }
            rows.add(new BulkResponse.Row(id, item.getName(), item.getSku(), outcome, message));
        }
        if (!r.dryRun()) log.info("Catalogue bulk {} tenant={} ok={} refused={}", action, tenantId, ok, refused);
        return new BulkResponse(r.dryRun(), ok, unchanged, refused, rows);
    }

    private String[] status(TenantId tenantId, CatalogueItem item, BulkRequest r, Caller caller) {
        if (item.getStatus() == r.status()) return new String[] {"UNCHANGED", "Already " + CatalogueRules.label(r.status()) + "."};
        String reason = CatalogueRules.checkTransition(item.getStatus(), r.status(), r.reason());
        if (!r.dryRun()) catalogueService.changeStatus(tenantId, item.getId(), new ItemStatusRequest(r.status(), r.reason(), null), caller);
        return new String[] {"OK", CatalogueRules.label(item.getStatus()) + " → " + CatalogueRules.label(r.status()) + (reason != null ? ": " + reason : "")};
    }

    private String[] category(TenantId tenantId, CatalogueItem item, BulkRequest r, Caller caller, String name) {
        if (item.isVariant()) throw new IllegalArgumentException("A variant follows its main item's category. Change the main item.");
        if (item.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be edited.");
        if (item.getCategory() != null && item.getCategory().getId().equals(r.categoryId())) return new String[] {"UNCHANGED", "Already in " + name + "."};
        if (!r.dryRun()) catalogueService.updateItem(tenantId, item.getId(), new CreateItemRequest(item.getName(), item.getDescription(), r.categoryId(), item.getUnit(),
                item.getDefaultPrice(), item.getVatRate(), item.getSku(), item.getBarcode(), item.getBrand(), item.getItemType(), item.getCostPrice(),
                item.isVatExempt(), null, item.getVersion(), null), caller);
        return new String[] {"OK", "→ " + name};
    }

    private String[] price(TenantId tenantId, CatalogueItem item, boolean hasVariants, BigDecimal percent, UUID listId, BulkRequest r, Caller caller) {
        if (hasVariants) throw new IllegalArgumentException("This item is sold through its variants. Select the variants instead.");
        if (item.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be re-priced.");
        BigDecimal now = item.getDefaultPrice();
        BigDecimal next = BulkRules.newPrice(now, percent);
        if (next.compareTo(now) == 0) return new String[] {"UNCHANGED", "The price stays " + now.toPlainString() + "."};
        PricingRules.checkAboveMinimum(next, item.getMinPrice());
        boolean approval = pricing.approvalRequired(tenantId);
        if (!r.dryRun()) pricing.propose(tenantId, item.getId(), new PriceChangeRequest(listId, next, r.effectiveFrom(), r.reason()), caller);
        return new String[] {"OK", now.toPlainString() + " → " + next.toPlainString() + (approval ? " (waits for approval)" : "")};
    }
}
