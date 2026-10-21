package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueVariantOptionRepository;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

/**
 * Variants of an item. A variant is a catalogue item of its own (own SKU, barcode, price) that points at its parent and
 * is named "Parent — Large / Red". Rules that need no database live in {@link VariantRules}.
 * Editing a variant goes through {@link CatalogueService#updateItem}, so duplicate checks, the price-approval rule and
 * history apply to it exactly as to any other item.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueVariantService {

    private static final UUID NO_ID = new UUID(0L, 0L);

    private final CatalogueItemRepository itemRepository;
    private final CatalogueVariantOptionRepository optionRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final CatalogueService catalogueService;

    @Transactional(readOnly = true)
    public VariantsResponse view(TenantId tenantId, UUID parentId, Caller caller) {
        var parent = load(tenantId, parentId);
        return build(tenantId, parent, caller);
    }

    /**
     * Adds the chosen variants. The first time, the options are stored; later calls must use the same options (values can
     * be added). A combination that already exists, or a SKU, barcode or name already in use, is refused and nothing is saved.
     */
    @Transactional
    public VariantsResponse create(TenantId tenantId, UUID parentId, CreateVariantsRequest r, Caller caller) {
        var parent = load(tenantId, parentId);
        if (parent.isVariant()) throw new IllegalArgumentException("A variant cannot have variants of its own.");
        if (parent.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot get variants. Restore it to a draft first.");
        if (r == null || r.options() == null) throw new IllegalArgumentException("Add at least one option, for example Size.");
        if (r.variants() == null || r.variants().isEmpty()) throw new IllegalArgumentException("Choose at least one variant to create.");

        List<String> names = VariantRules.optionNames(r.options().stream().map(VariantOptionDto::name).toList());
        List<List<String>> values = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) values.add(VariantRules.values(names.get(i), r.options().get(i).values()));

        var existing = itemRepository.findVariants(tenantId, parentId);
        if (existing.size() + r.variants().size() > VariantRules.MAX_PER_PARENT)
            throw new IllegalArgumentException("An item can have at most " + VariantRules.MAX_PER_PARENT + " variants.");
        var oldOptions = optionRepository.findForParent(tenantId, parentId);
        if (!existing.isEmpty()) {
            List<String> oldNames = oldOptions.stream().map(CatalogueVariantOption::getName).toList();
            if (!sameIgnoringCase(oldNames, names))
                throw new IllegalArgumentException("This item already has the options " + String.join(", ", oldNames) + ". Use the same options, or delete its variants first.");
            for (int i = 0; i < names.size(); i++) values.set(i, merge(oldOptions.get(i).values(), values.get(i)));
        }
        optionRepository.deleteForParent(tenantId, parentId);
        optionRepository.flush();
        for (int i = 0; i < names.size(); i++) optionRepository.save(CatalogueVariantOption.create(tenantId, parentId, names.get(i), i, values.get(i)));

        Set<String> keys = new HashSet<>();
        existing.forEach(v -> keys.add(v.getVariantKey()));
        Set<String> skus = new HashSet<>(), barcodes = new HashSet<>();
        var status = parent.getStatus() == CatalogueItemStatus.ACTIVE ? CatalogueItemStatus.ACTIVE : CatalogueItemStatus.DRAFT;
        var created = new ArrayList<CatalogueItem>();
        for (var row : r.variants()) {
            List<String> picked = pick(names, values, row.values());
            String key = VariantRules.key(names, picked);
            if (!keys.add(key)) throw new ConflictException("The variant " + VariantRules.label(picked) + " already exists.");
            String name = VariantRules.variantName(parent.getName(), picked);
            String sku = CatalogueRules.normalizeSku(row.sku());
            String barcode = CatalogueRules.normalizeBarcode(row.barcode());
            if (sku != null && (!skus.add(sku) || itemRepository.skuTaken(tenantId, sku, NO_ID))) throw new ConflictException("Another item already uses the SKU '" + sku + "'.");
            if (barcode != null && (!barcodes.add(barcode.toLowerCase(Locale.ROOT)) || itemRepository.barcodeTaken(tenantId, barcode, NO_ID))) throw new ConflictException("Another item already uses the barcode '" + barcode + "'.");
            if (itemRepository.nameTaken(tenantId, name, NO_ID)) throw new ConflictException("An item named '" + name + "' already exists.");
            BigDecimal price = CatalogueRules.checkMoney("The price", row.defaultPrice() != null ? row.defaultPrice() : parent.getDefaultPrice());
            BigDecimal cost = caller.canWritePrices() ? CatalogueRules.checkMoney("The cost price", row.costPrice()) : null;
            var details = new CatalogueItem.Details(name, parent.getDescription(), sku, barcode, parent.getBrand(), parent.getItemType(),
                    parent.getUnit(), price, cost, parent.getVatRate(), parent.isVatExempt());
            var variant = CatalogueItem.create(tenantId, parent.getCategory(), details, status);
            variant.asVariant(parentId, key, VariantRules.label(picked), String.join("|", picked));
            created.add(variant);
        }
        for (var v : created) {
            itemRepository.save(v);
            audit(tenantId, v, CatalogueAuditAction.CREATED, "Created as a variant (" + v.getVariantLabel() + ") of " + parent.getName(), caller);
        }
        audit(tenantId, parent, CatalogueAuditAction.UPDATED, created.size() + (created.size() == 1 ? " variant added" : " variants added"), caller);
        log.info("Created {} variants for item={} tenant={}", created.size(), parentId, tenantId);
        return build(tenantId, parent, caller);
    }

    /** Edits SKU, barcode, price and cost of one variant. */
    @Transactional
    public CatalogueItemResponse update(TenantId tenantId, UUID parentId, UUID variantId, UpdateVariantRequest r, Caller caller) {
        var v = load(tenantId, variantId);
        if (!parentId.equals(v.getParentId())) throw new ResourceNotFoundException("Variant", variantId.toString());
        var req = new CreateItemRequest(v.getName(), v.getDescription(), v.getCategory() == null ? null : v.getCategory().getId(), v.getUnit(),
                r.defaultPrice() != null ? r.defaultPrice() : v.getDefaultPrice(), v.getVatRate(), r.sku(), r.barcode(), v.getBrand(), v.getItemType(),
                r.costPrice() != null ? r.costPrice() : v.getCostPrice(), v.isVatExempt(), null, r.version());
        return catalogueService.updateItem(tenantId, variantId, req, caller);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VariantsResponse build(TenantId tenantId, CatalogueItem parent, Caller caller) {
        var options = optionRepository.findForParent(tenantId, parent.getId()).stream()
                .map(o -> new VariantOptionDto(o.getName(), o.values())).toList();
        var rows = itemRepository.findVariants(tenantId, parent.getId()).stream().map(v -> new VariantRowResponse(
                v.getId(), v.getName(), v.getVariantLabel(), Arrays.asList(v.getVariantValues().split("\\|")), v.getSku(), v.getBarcode(),
                v.getStatus().name(), v.getDefaultPrice(), caller.canSeePrices() ? v.getCostPrice() : null, v.getVersion())).toList();
        return new VariantsResponse(parent.getId(), parent.getName(), parent.getSku(), parent.getDefaultPrice(), options, rows);
    }

    /** Matches the chosen values to the option's own spelling; a value the option does not offer is refused. */
    private static List<String> pick(List<String> names, List<List<String>> offered, List<String> chosen) {
        if (chosen == null || chosen.size() != names.size()) throw new IllegalArgumentException("Choose one value for each option.");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String c = chosen.get(i) == null ? "" : chosen.get(i).trim();
            String match = offered.get(i).stream().filter(o -> o.equalsIgnoreCase(c)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("'" + c + "' is not a value of the option '" + names.get(0 + out.size()) + "'."));
            out.add(match);
        }
        return out;
    }

    private static List<String> merge(List<String> old, List<String> added) {
        List<String> out = new ArrayList<>(old);
        for (String a : added) if (out.stream().noneMatch(o -> o.equalsIgnoreCase(a))) out.add(a);
        if (out.size() > VariantRules.MAX_VALUES) throw new IllegalArgumentException("An option can have at most " + VariantRules.MAX_VALUES + " values.");
        return out;
    }

    private static boolean sameIgnoringCase(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).equalsIgnoreCase(b.get(i))) return false;
        return true;
    }

    private CatalogueItem load(TenantId tenantId, UUID id) {
        return itemRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", id.toString()));
    }

    private void audit(TenantId tenantId, CatalogueItem item, CatalogueAuditAction action, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", item.getId(), item.getName(), action, detail, caller.userId()));
    }
}
