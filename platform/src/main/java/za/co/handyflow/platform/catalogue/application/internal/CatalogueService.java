package za.co.handyflow.platform.catalogue.application.internal;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue items and categories. Rules that need no database live in {@link CatalogueRules}.
 * Every read and write is scoped to the tenant, and every change leaves a line in the history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueService {

    /** Matches no real row; used as "exclude nothing" in duplicate checks. */
    private static final UUID NO_ID = new UUID(0L, 0L);

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final VatRateProvider vatRateProvider;
    private final CatalogueStructureService structure;
    private final CataloguePricingService pricing;

    /** Who is asking and what they may do. Built by the controller from the signed-in user's authorities. */
    public record Caller(UUID userId, boolean canSeePrices, boolean canWritePrices, boolean canArchive, boolean canApprovePrices, boolean canApproveItems) {
        public Caller(UUID userId, boolean canSeePrices, boolean canWritePrices, boolean canArchive, boolean canApprovePrices) {
            this(userId, canSeePrices, canWritePrices, canArchive, canApprovePrices, false);
        }
        public Caller(UUID userId, boolean canSeePrices, boolean canWritePrices, boolean canArchive) {
            this(userId, canSeePrices, canWritePrices, canArchive, false, false);
        }
    }

    /** The filters of the catalogue list. All optional. */
    public record ItemFilter(String q, UUID categoryId, CatalogueItemStatus status, CatalogueItemType type) {}

    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Pickers (quotes, POS, supply chain): sellable items only. */
    @Transactional(readOnly = true)
    public java.util.List<CatalogueItemSummary> searchItems(TenantId tenantId, String query) {
        var items = (query == null || query.isBlank())
                ? itemRepository.findAllSellable(tenantId)
                : itemRepository.searchSellable(tenantId, query.trim());
        return items.stream().map(CatalogueFacadeImpl::toSummary).toList();
    }

    /** The catalogue list: every live item, paged, filtered and sorted on the server. */
    @Transactional(readOnly = true)
    public Page<CatalogueItemResponse> listItems(TenantId tenantId, ItemFilter f, int page, int size, String sort, boolean desc, Caller caller) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), sortFor(sort, desc));
        List<UUID> categoryIds = f.categoryId() == null ? null : StructureRules.subtree(f.categoryId(), parentMap(tenantId));
        Specification<CatalogueItem> spec = (root, query, cb) -> {
            var ps = new ArrayList<Predicate>();
            ps.add(cb.equal(root.get("tenantId"), tenantId));
            ps.add(cb.isNull(root.get("deletedAt")));
            // Variants are reached through their parent; a search looks at them too, so a variant's SKU or barcode still finds it.
            if (f.q() == null || f.q().isBlank()) ps.add(cb.isNull(root.get("parentId")));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("sku")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("barcode")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("brand")), like, '\\')));
            }
            if (categoryIds != null) ps.add(root.get("category").get("id").in(categoryIds));
            if (f.status() != null) ps.add(cb.equal(root.get("status"), f.status()));
            if (f.type() != null) ps.add(cb.equal(root.get("itemType"), f.type()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<CatalogueItem> found = itemRepository.findAll(spec, pageable);
        Map<UUID, Long> counts = new HashMap<>();
        if (!found.isEmpty()) itemRepository.variantCounts(tenantId, found.getContent().stream().map(CatalogueItem::getId).toList())
                .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        return found.map(i -> toResponse(i, caller).withVariantCount(counts.getOrDefault(i.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CatalogueItemResponse getItem(TenantId tenantId, UUID itemId, Caller caller) {
        return toResponse(load(tenantId, itemId), caller).withAttributes(structure.valuesFor(tenantId, itemId))
                .withVariantCount(itemRepository.countVariants(tenantId, itemId));
    }

    @Transactional(readOnly = true)
    public Page<CatalogueHistoryResponse> history(TenantId tenantId, UUID entityId, int page, int size, Caller caller) {
        return auditRepository.history(tenantId, entityId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                .map(e -> new CatalogueHistoryResponse(e.getId(), e.getAction().name(), HistoryRules.redact(e.getAction(), e.getDetail(), caller.canSeePrices()), e.getActorId(), e.getCreatedAt()));
    }

    // ── Item writes ───────────────────────────────────────────────────────────

    @Transactional
    public CatalogueItemResponse createItem(TenantId tenantId, CreateItemRequest r, Caller caller) {
        // When the tenant approves items before they go live, someone who cannot approve creates drafts.
        boolean mustBeApproved = !caller.canApproveItems() && pricing.itemApprovalRequired(tenantId);
        CatalogueItemStatus status = r.status() == null ? (mustBeApproved ? CatalogueItemStatus.DRAFT : CatalogueItemStatus.ACTIVE) : r.status();
        if (status != CatalogueItemStatus.ACTIVE && status != CatalogueItemStatus.DRAFT)
            throw new IllegalArgumentException("A new item can only start as a draft or active.");
        if (status == CatalogueItemStatus.ACTIVE && mustBeApproved)
            throw new IllegalArgumentException("New items need approval before they go live. Save it as a draft and submit it for approval.");
        if (r.costPrice() != null && !caller.canWritePrices()) throw new AccessDeniedException("You are not allowed to set the cost price.");

        CatalogueCategory category = category(tenantId, r.categoryId());
        var details = details(tenantId, r, null, caller);
        checkDuplicates(tenantId, details, null);
        var attributes = structure.check(tenantId, category == null ? null : category.getId(), r.attributes(),
                status != CatalogueItemStatus.DRAFT, null);

        CatalogueItem item = CatalogueItem.create(tenantId, category, details, status);
        itemRepository.save(item);
        structure.store(tenantId, item.getId(), attributes);
        audit(tenantId, item, CatalogueAuditAction.CREATED, "Created as " + CatalogueRules.label(status)
                + (item.getSku() != null ? " (SKU " + item.getSku() + ")" : ""), caller);
        log.info("Created catalogue item={} tenant={}", item.getName(), tenantId);
        return toResponse(item, caller).withAttributes(structure.valuesFor(tenantId, item.getId()));
    }

    @Transactional
    public CatalogueItemResponse updateItem(TenantId tenantId, UUID itemId, CreateItemRequest r, Caller caller) {
        CatalogueItem item = load(tenantId, itemId);
        checkVersion(item, r.version());
        if (item.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be edited. Restore it to a draft first.");
        if (r.costPrice() != null && !caller.canWritePrices() && differs(r.costPrice(), item.getCostPrice()))
            throw new AccessDeniedException("You are not allowed to change the cost price.");

        CatalogueCategory category = category(tenantId, r.categoryId());
        var details = details(tenantId, r, item, caller);
        checkDuplicates(tenantId, details, item.getId());
        var attributes = structure.check(tenantId, category == null ? null : category.getId(), r.attributes(),
                item.getStatus() != CatalogueItemStatus.DRAFT, structure.stored(tenantId, item.getId()));
        BigDecimal oldPrice = item.getDefaultPrice();
        boolean priceEdited = differs(details.defaultPrice(), oldPrice);
        if (priceEdited) pricing.requireDirectEditAllowed(tenantId);

        var before = item.snapshot();
        item.update(category, details);
        var after = item.snapshot();
        String change = CatalogueRules.describeChanges(before, after);
        String attributeChange = structure.store(tenantId, item.getId(), attributes);
        if (priceEdited) pricing.recordDirectChange(tenantId, item, oldPrice, item.getDefaultPrice(), caller);
        if (!item.isVariant()) propagateToVariants(tenantId, item, details, category, caller);
        if (!change.isEmpty()) {
            itemRepository.save(item);
            audit(tenantId, item, CatalogueRules.priceChanged(before, after) ? CatalogueAuditAction.PRICE_CHANGED : CatalogueAuditAction.UPDATED, change, caller);
        }
        if (!attributeChange.isEmpty()) audit(tenantId, item, CatalogueAuditAction.UPDATED, "attributes: " + attributeChange, caller);
        log.info("Updated catalogue item={} tenant={}", itemId, tenantId);
        return toResponse(item, caller).withAttributes(structure.valuesFor(tenantId, item.getId()));
    }

    @Transactional
    public CatalogueItemResponse changeStatus(TenantId tenantId, UUID itemId, ItemStatusRequest r, Caller caller) {
        CatalogueItem item = load(tenantId, itemId);
        checkVersion(item, r.version());
        if (r.status().needsArchivePermission() && !caller.canArchive())
            throw new AccessDeniedException("You are not allowed to discontinue or archive items.");
        var from = item.getStatus();
        if (from == CatalogueItemStatus.DRAFT && r.status() == CatalogueItemStatus.ACTIVE && !caller.canApproveItems() && pricing.itemApprovalRequired(tenantId))
            throw new IllegalArgumentException("This draft needs approval before it goes live. Submit it for approval.");
        String reason = CatalogueRules.checkTransition(from, r.status(), r.reason());
        if (r.status() == CatalogueItemStatus.ACTIVE && (item.getUnit() == null || item.getDefaultPrice() == null))
            throw new IllegalArgumentException("The item needs a unit and a price before it can be activated.");
        if (r.status() == CatalogueItemStatus.ACTIVE)
            structure.check(tenantId, item.getCategory() == null ? null : item.getCategory().getId(), null, true, structure.stored(tenantId, item.getId()));
        if (r.status() == CatalogueItemStatus.ACTIVE && item.isVariant()) {
            var parent = load(tenantId, item.getParentId());
            if (parent.getStatus() != CatalogueItemStatus.ACTIVE) throw new IllegalArgumentException("Activate the main item '" + parent.getName() + "' first.");
        }
        item.changeStatus(r.status(), reason);
        itemRepository.save(item);
        audit(tenantId, item, CatalogueAuditAction.STATUS_CHANGED,
                CatalogueRules.label(from) + " -> " + CatalogueRules.label(r.status()) + (reason != null ? ": " + reason : ""), caller);
        if (!item.isVariant() && r.status() != CatalogueItemStatus.ACTIVE) cascadeStatus(tenantId, item, r.status(), reason, caller);
        return toResponse(item, caller);
    }

    @Transactional
    public void softDeleteItem(TenantId tenantId, UUID itemId, Caller caller) {
        CatalogueItem item = load(tenantId, itemId);
        var variants = itemRepository.findVariants(tenantId, itemId);
        item.softDelete(caller.userId());
        itemRepository.save(item);
        audit(tenantId, item, CatalogueAuditAction.DELETED, variants.isEmpty() ? "Deleted" : "Deleted with its " + variants.size() + (variants.size() == 1 ? " variant" : " variants"), caller);
        for (var v : variants) {
            v.softDelete(caller.userId());
            itemRepository.save(v);
            audit(tenantId, v, CatalogueAuditAction.DELETED, "Deleted with " + item.getName(), caller);
        }
    }

    // ── Variants follow their parent ──────────────────────────────────────────

    /** A parent's name, category, unit, brand, type and VAT are the variants' too; their SKU, barcode, price and cost stay their own. */
    private void propagateToVariants(TenantId tenantId, CatalogueItem parent, CatalogueItem.Details d, CatalogueCategory category, Caller caller) {
        for (var v : itemRepository.findVariants(tenantId, parent.getId())) {
            String name = VariantRules.variantName(d.name(), java.util.Arrays.asList(v.getVariantValues().split("\\|")));
            if (!name.equals(v.getName()) && itemRepository.nameTaken(tenantId, name, v.getId()))
                throw new ConflictException("An item named '" + name + "' already exists, so the variants cannot be renamed.");
            var vd = new CatalogueItem.Details(name, d.description(), v.getSku(), v.getBarcode(), d.brand(), d.type(), d.unit(),
                    v.getDefaultPrice(), v.getCostPrice(), d.vatRate(), d.vatExempt());
            var before = v.snapshot();
            v.update(category, vd);
            String change = CatalogueRules.describeChanges(before, v.snapshot());
            if (!change.isEmpty()) {
                itemRepository.save(v);
                audit(tenantId, v, CatalogueAuditAction.UPDATED, "Followed " + parent.getName() + ": " + change, caller);
            }
        }
    }

    /** Suspending, discontinuing or archiving a parent does the same to its variants where that move is allowed. */
    private void cascadeStatus(TenantId tenantId, CatalogueItem parent, CatalogueItemStatus to, String reason, Caller caller) {
        for (var v : itemRepository.findVariants(tenantId, parent.getId())) {
            try {
                String r = CatalogueRules.checkTransition(v.getStatus(), to, reason);
                var from = v.getStatus();
                v.changeStatus(to, r);
                itemRepository.save(v);
                audit(tenantId, v, CatalogueAuditAction.STATUS_CHANGED, CatalogueRules.label(from) + " -> " + CatalogueRules.label(to) + " with " + parent.getName(), caller);
            } catch (IllegalArgumentException skipped) {
                // This variant cannot make that move (already there, or not allowed from its status); it is left as it is.
            }
        }
    }

    // ── Categories ────────────────────────────────────────────────────────────

    /** Every live category with its place in the tree. itemCount is the items directly in it. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(TenantId tenantId) {
        var all = categoryRepository.findAllActive(tenantId);
        Map<UUID, UUID> parents = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        all.forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
        return all.stream()
                .map(c -> response(c, parents, names, itemRepository.countLiveInCategory(tenantId, c)))
                .sorted(java.util.Comparator.comparing(CategoryResponse::path, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(TenantId tenantId, CreateCategoryRequest r, Caller caller) {
        String name = categoryName(r.name());
        var parents = parentMap(tenantId);
        UUID parentId = checkedParent(tenantId, r.parentId(), null, 1, parents);
        if (categoryRepository.nameTaken(tenantId, name, parentId, NO_ID)) throw new ConflictException(duplicateCategoryMessage(name, parentId));
        CatalogueCategory cat = CatalogueCategory.create(tenantId, name, r.description(), parentId);
        categoryRepository.save(cat);
        audit(tenantId, "CATEGORY", cat.getId(), cat.getName(), CatalogueAuditAction.CREATED, parentId == null ? "Created" : "Created under " + pathOf(tenantId, parentId), caller);
        return categoryResponse(tenantId, cat, 0L);
    }

    @Transactional
    public CategoryResponse updateCategory(TenantId tenantId, UUID categoryId, CreateCategoryRequest r, Caller caller) {
        CatalogueCategory cat = categoryRepository.findActiveById(tenantId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId.toString()));
        String name = categoryName(r.name());
        var parents = parentMap(tenantId);
        UUID parentId = checkedParent(tenantId, r.parentId(), cat.getId(), subtreeHeight(cat.getId(), parents), parents);
        if (categoryRepository.nameTaken(tenantId, name, parentId, cat.getId())) throw new ConflictException(duplicateCategoryMessage(name, parentId));
        String old = cat.getName();
        boolean moved = !java.util.Objects.equals(parentId, cat.getParentId());
        String from = moved ? pathOf(tenantId, cat.getParentId()) : null;
        cat.update(name, r.description());
        if (moved) cat.moveTo(parentId);
        categoryRepository.save(cat);
        var parts = new ArrayList<String>();
        if (!old.equals(name)) parts.add("name: " + old + " -> " + name);
        if (moved) parts.add("moved: " + (from.isEmpty() ? "top level" : from) + " -> " + (parentId == null ? "top level" : pathOf(tenantId, parentId)));
        audit(tenantId, "CATEGORY", cat.getId(), cat.getName(), CatalogueAuditAction.UPDATED, parts.isEmpty() ? "Updated" : String.join("; ", parts), caller);
        return categoryResponse(tenantId, cat, itemRepository.countLiveInCategory(tenantId, cat));
    }

    /** A category that still holds items, sub-categories or attributes is not deleted: that used to remove items silently. */
    @Transactional
    public void deleteCategory(TenantId tenantId, UUID categoryId, Caller caller) {
        CatalogueCategory cat = categoryRepository.findActiveById(tenantId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId.toString()));
        long n = itemRepository.countLiveInCategory(tenantId, cat);
        if (n > 0) throw new ConflictException("This category still has " + n + (n == 1 ? " item" : " items") + ". Move or delete them first.");
        long kids = parentMap(tenantId).values().stream().filter(categoryId::equals).count();
        if (kids > 0) throw new ConflictException("This category still has " + kids + (kids == 1 ? " sub-category" : " sub-categories") + ". Move or delete them first.");
        long attrs = structure.countDefinitionsFor(tenantId, categoryId);
        if (attrs > 0) throw new ConflictException("This category still has " + attrs + (attrs == 1 ? " attribute" : " attributes") + ". Delete or re-home them first.");
        cat.softDelete(caller.userId());
        categoryRepository.save(cat);
        audit(tenantId, "CATEGORY", cat.getId(), cat.getName(), CatalogueAuditAction.DELETED, "Deleted", caller);
    }

    // ── Category tree helpers ─────────────────────────────────────────────────

    private Map<UUID, UUID> parentMap(TenantId tenantId) {
        Map<UUID, UUID> parents = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> parents.put(c.getId(), c.getParentId()));
        return parents;
    }

    /** Checks the chosen parent exists and the placement is allowed; returns the parent id (null = top level). */
    private UUID checkedParent(TenantId tenantId, UUID parentId, UUID self, int height, Map<UUID, UUID> parents) {
        if (parentId == null) return null;
        if (!parents.containsKey(parentId)) throw new ResourceNotFoundException("Category", parentId.toString());
        StructureRules.checkPlacement(self, parentId, height, parents::get);
        return parentId;
    }

    /** Levels in the subtree under (and including) this category. */
    private static int subtreeHeight(UUID id, Map<UUID, UUID> parents) {
        int base = StructureRules.depthOf(id, parents::get), best = 1;
        for (UUID d : StructureRules.subtree(id, parents)) best = Math.max(best, StructureRules.depthOf(d, parents::get) - base + 1);
        return best;
    }

    private String pathOf(TenantId tenantId, UUID id) {
        if (id == null) return "";
        Map<UUID, UUID> parents = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
        return StructureRules.pathOf(id, parents::get, names::get);
    }

    private CategoryResponse categoryResponse(TenantId tenantId, CatalogueCategory cat, long count) {
        Map<UUID, UUID> parents = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
        parents.put(cat.getId(), cat.getParentId());
        names.put(cat.getId(), cat.getName());
        return response(cat, parents, names, count);
    }

    private static CategoryResponse response(CatalogueCategory c, Map<UUID, UUID> parents, Map<UUID, String> names, long count) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getSortOrder(), count, c.getParentId(),
                StructureRules.pathOf(c.getId(), parents::get, names::get), StructureRules.depthOf(c.getId(), parents::get));
    }

    private String duplicateCategoryMessage(String name, UUID parentId) {
        return parentId == null ? "A category named '" + name + "' already exists at the top level."
                : "A category named '" + name + "' already exists in that category.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CatalogueItem load(TenantId tenantId, UUID itemId) {
        return itemRepository.findActiveById(tenantId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
    }

    private CatalogueCategory category(TenantId tenantId, UUID id) {
        if (id == null) return null;
        return categoryRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id.toString()));
    }

    private static String categoryName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Category name is required.");
        if (s.length() > 100) throw new IllegalArgumentException("The category name can be at most 100 characters.");
        return s;
    }

    private static void checkVersion(CatalogueItem item, Long sent) {
        if (sent != null && !sent.equals(item.getVersion()))
            throw new ConflictException("This item was changed by someone else. Reload it and try again.");
    }

    /** Cleans and checks the request. When the caller may not write prices, the existing cost is kept. */
    private CatalogueItem.Details details(TenantId tenantId, CreateItemRequest r, CatalogueItem existing, Caller caller) {
        boolean exempt = Boolean.TRUE.equals(r.vatExempt());
        BigDecimal vat = r.vatRate() != null ? r.vatRate() : vatRateProvider.ratePercent();
        BigDecimal cost = caller.canWritePrices() ? CatalogueRules.checkMoney("The cost price", r.costPrice())
                : existing != null ? existing.getCostPrice() : null;
        if (r.unit() == null || r.unit().isBlank()) throw new IllegalArgumentException("Unit is required.");
        String unit = structure.resolveUnit(tenantId, r.unit(), existing == null ? null : existing.getUnit());
        return new CatalogueItem.Details(
                CatalogueRules.normalizeName(r.name()),
                r.description() == null || r.description().isBlank() ? null : r.description().trim(),
                CatalogueRules.normalizeSku(r.sku()),
                CatalogueRules.normalizeBarcode(r.barcode()),
                CatalogueRules.normalizeBrand(r.brand()),
                r.itemType() == null ? CatalogueItemType.PRODUCT : r.itemType(),
                unit,
                CatalogueRules.checkMoney("The price", r.defaultPrice()),
                cost,
                CatalogueRules.effectiveVat(exempt, vat.setScale(2, RoundingMode.HALF_UP)),
                exempt);
    }

    private void checkDuplicates(TenantId tenantId, CatalogueItem.Details d, UUID selfId) {
        UUID exclude = selfId == null ? NO_ID : selfId;
        if (itemRepository.nameTaken(tenantId, d.name(), exclude)) throw new ConflictException("An item named '" + d.name() + "' already exists.");
        if (d.sku() != null && itemRepository.skuTaken(tenantId, d.sku(), exclude)) throw new ConflictException("Another item already uses the SKU '" + d.sku() + "'.");
        if (d.barcode() != null && itemRepository.barcodeTaken(tenantId, d.barcode(), exclude)) throw new ConflictException("Another item already uses the barcode '" + d.barcode() + "'.");
    }

    private void audit(TenantId tenantId, CatalogueItem item, CatalogueAuditAction action, String detail, Caller caller) {
        audit(tenantId, "ITEM", item.getId(), item.getName(), action, detail, caller);
    }

    private void audit(TenantId tenantId, String type, UUID id, String name, CatalogueAuditAction action, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, type, id, name, action, detail, caller.userId()));
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a != b : a.compareTo(b) != 0;
    }

    private static Sort sortFor(String sort, boolean desc) {
        String field = switch (sort == null ? "" : sort) {
            case "sku" -> "sku";
            case "price" -> "defaultPrice";
            case "status" -> "status";
            case "type" -> "itemType";
            case "updated" -> "updatedAt";
            default -> "name";
        };
        Sort s = desc ? Sort.by(field).descending() : Sort.by(field).ascending();
        return "name".equals(field) ? s : s.and(Sort.by("name").ascending());
    }

    static CatalogueItemResponse toResponse(CatalogueItem i, Caller caller) {
        BigDecimal cost = caller.canSeePrices() ? i.getCostPrice() : null;
        BigDecimal margin = null;
        if (cost != null && i.getDefaultPrice() != null && i.getDefaultPrice().signum() > 0)
            margin = i.getDefaultPrice().subtract(cost).multiply(BigDecimal.valueOf(100)).divide(i.getDefaultPrice(), 1, RoundingMode.HALF_UP);
        if (!caller.canSeePrices()) margin = null;
        return new CatalogueItemResponse(i.getId(), i.getName(), i.getDescription(), i.getSku(), i.getBarcode(), i.getBrand(),
                i.getItemType().name(), i.getStatus().name(), i.getStatusReason(), i.getStatusChangedAt(), i.getUnit(),
                i.getDefaultPrice(), i.getVatRate(), i.isVatExempt(),
                i.getCategory() != null ? i.getCategory().getId() : null,
                i.getCategory() != null ? i.getCategory().getName() : null,
                cost, margin, i.getVersion(), i.getCreatedAt(), i.getUpdatedAt(), null, i.getParentId(), i.getVariantLabel(), 0L);
    }
}
