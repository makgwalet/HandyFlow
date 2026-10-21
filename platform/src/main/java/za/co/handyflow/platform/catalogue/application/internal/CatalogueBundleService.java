package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.model.BundleRules.Component;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueBundleComponentRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.BundleLineRequest;
import za.co.handyflow.platform.catalogue.dto.BundleRequest;
import za.co.handyflow.platform.catalogue.dto.BundleResponse;
import za.co.handyflow.platform.catalogue.dto.UsedInResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Packages: an item of type Package made of other catalogue items. The package keeps its own price (set the usual way);
 * this service keeps the parts and shows what the parts are worth, so the saving is visible. Money is hidden from people
 * who may not see prices. Stock for the parts is the job of the stock module, not the catalogue.
 */
@Service
@RequiredArgsConstructor
public class CatalogueBundleService {

    private final CatalogueBundleComponentRepository componentRepository;
    private final CatalogueItemRepository itemRepository;
    private final CatalogueAuditEventRepository auditRepository;

    @Transactional(readOnly = true)
    public BundleResponse view(TenantId tenantId, UUID bundleId, Caller caller) {
        return build(tenantId, bundle(tenantId, bundleId), caller);
    }

    /** Replaces the package's parts with exactly the list given. Nothing is saved if any line is refused. */
    @Transactional
    public BundleResponse replace(TenantId tenantId, UUID bundleId, BundleRequest r, Caller caller) {
        var bundle = bundle(tenantId, bundleId);
        if (r == null || r.components() == null) throw new IllegalArgumentException("Add at least one component.");
        List<BundleRules.Line> asked = r.components().stream()
                .map(l -> new BundleRules.Line(l == null ? null : l.itemId(), l == null ? null : l.quantity())).toList();

        Set<UUID> ids = asked.stream().map(BundleRules.Line::itemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CatalogueItem> items = itemRepository.findLiveByIds(tenantId, ids).stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        Set<UUID> withVariants = new HashSet<>(itemRepository.parentIdsWithVariants(tenantId));
        Map<UUID, Component> found = new HashMap<>();
        items.forEach((id, i) -> found.put(id, new Component(id, i.getItemType(), i.getStatus(), i.isDeleted(), withVariants.contains(id))));

        List<BundleRules.Line> lines = BundleRules.validate(bundleId, bundle.getItemType(), asked, found);

        componentRepository.deleteForBundle(tenantId, bundleId);
        int order = 0;
        for (var l : lines) componentRepository.save(CatalogueBundleComponent.of(tenantId, bundleId, l.itemId(), l.quantity(), order++));
        auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", bundle.getId(), bundle.getName(), CatalogueAuditAction.BUNDLE_CHANGED,
                "Package now has " + lines.size() + (lines.size() == 1 ? " component" : " components"), caller.userId()));
        return build(tenantId, bundle, caller);
    }

    /** The packages that contain this item. */
    @Transactional(readOnly = true)
    public List<UsedInResponse> usedIn(TenantId tenantId, UUID itemId) {
        itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
        var uses = componentRepository.usedBy(tenantId, itemId);
        if (uses.isEmpty()) return List.of();
        Map<UUID, CatalogueItem> bundles = itemRepository.findLiveByIds(tenantId, uses.stream().map(CatalogueBundleComponent::getBundleItemId).toList())
                .stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        return uses.stream().filter(u -> bundles.containsKey(u.getBundleItemId())).map(u -> {
            var b = bundles.get(u.getBundleItemId());
            return new UsedInResponse(b.getId(), b.getName(), b.getSku(), b.getStatus().name(), u.getQuantity());
        }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BundleResponse build(TenantId tenantId, CatalogueItem bundle, Caller caller) {
        var parts = componentRepository.forBundle(tenantId, bundle.getId());
        Map<UUID, CatalogueItem> items = parts.isEmpty() ? Map.of() : itemRepository.findLiveByIds(tenantId,
                parts.stream().map(CatalogueBundleComponent::getComponentItemId).toList()).stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        boolean money = caller.canSeePrices();

        List<BigDecimal> prices = new ArrayList<>();
        List<BigDecimal> quantities = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        List<BundleResponse.Line> lines = new ArrayList<>();
        for (var p : parts) {
            var i = items.get(p.getComponentItemId());
            if (i == null) continue;   // a part that was deleted since; it shows up in the data-quality checks
            prices.add(i.getDefaultPrice());
            quantities.add(p.getQuantity());
            if (i.getCostPrice() != null) cost = cost.add(i.getCostPrice().multiply(p.getQuantity()));
            BigDecimal unit = money ? i.getDefaultPrice() : null;
            BigDecimal lineTotal = money && i.getDefaultPrice() != null ? i.getDefaultPrice().multiply(p.getQuantity()).setScale(2, RoundingMode.HALF_UP) : null;
            lines.add(new BundleResponse.Line(i.getId(), i.getName(), i.getSku(), i.getStatus().name(), p.getQuantity(), unit, lineTotal));
        }
        BigDecimal total = BundleRules.total(prices, quantities);
        int unpriced = BundleRules.unpriced(prices);
        BigDecimal saving = BundleRules.saving(bundle.getDefaultPrice(), total, unpriced);
        return new BundleResponse(bundle.getId(), bundle.getName(), bundle.getSku(), bundle.getItemType().name(),
                money ? bundle.getDefaultPrice() : null, lines,
                money ? total : null, money ? cost.setScale(2, RoundingMode.HALF_UP) : null, money ? saving : null, unpriced);
    }

    private CatalogueItem bundle(TenantId tenantId, UUID id) {
        return itemRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", id.toString()));
    }
}
