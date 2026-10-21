package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Price lists, dated price changes, the minimum price and the per-tenant "price changes need approval" setting.
 * The default (Retail) list is the item's default price, which Invoicing, POS and Supply Chain keep reading; a change
 * to it updates that price on its effective date. Invoices keep the price they were created with.
 * Rules that need no database live in {@link PricingRules}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CataloguePricingService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final CatalogueItemRepository itemRepository;
    private final CataloguePriceListRepository listRepository;
    private final CataloguePriceChangeRepository changeRepository;
    private final CatalogueSettingRepository settingRepository;
    private final CatalogueAuditEventRepository auditRepository;

    private Clock clock = Clock.system(ZONE);

    /** Tests set a fixed clock. */
    void useClock(Clock clock) { this.clock = clock; }

    LocalDate today() { return LocalDate.now(clock); }

    // ── Settings ──────────────────────────────────────────────────────────────

    /** A tenant with no settings row is a new tenant: approval is required by default. */
    @Transactional(readOnly = true)
    public boolean approvalRequired(TenantId tenantId) {
        return settingRepository.findById(tenantId.getValue()).map(CatalogueSetting::isPriceApprovalRequired).orElse(true);
    }

    /** Items are approved before going live only when a tenant switched that on; no row means off. */
    @Transactional(readOnly = true)
    public boolean itemApprovalRequired(TenantId tenantId) {
        return settingRepository.findById(tenantId.getValue()).map(CatalogueSetting::isItemApprovalRequired).orElse(false);
    }

    @Transactional(readOnly = true)
    public CatalogueSettingsResponse settings(TenantId tenantId) {
        var s = settingRepository.findById(tenantId.getValue());
        return new CatalogueSettingsResponse(s.map(CatalogueSetting::isPriceApprovalRequired).orElse(true), s.map(CatalogueSetting::isItemApprovalRequired).orElse(false),
                s.map(CatalogueSetting::getUpdatedAt).orElse(null));
    }

    @Transactional
    public CatalogueSettingsResponse setItemApprovalRequired(TenantId tenantId, boolean required, Caller caller) {
        boolean was = itemApprovalRequired(tenantId);
        var s = settingRepository.findById(tenantId.getValue()).orElse(null);
        if (s == null) s = CatalogueSetting.of(tenantId.getValue(), approvalRequired(tenantId), caller.userId());   // keep the price default (new tenants: on)
        s.setItemApproval(required, caller.userId());
        settingRepository.save(s);
        if (was != required)
            audit(tenantId, "SETTING", tenantId.getValue(), "Item approval", CatalogueAuditAction.SETTING_CHANGED,
                    "New items " + (required ? "now need approval before they go live" : "no longer need approval"), caller.userId());
        return new CatalogueSettingsResponse(s.isPriceApprovalRequired(), s.isItemApprovalRequired(), s.getUpdatedAt());
    }

    @Transactional
    public CatalogueSettingsResponse setApprovalRequired(TenantId tenantId, boolean required, Caller caller) {
        boolean was = approvalRequired(tenantId);
        var s = settingRepository.findById(tenantId.getValue()).orElse(null);
        if (s == null) s = CatalogueSetting.of(tenantId.getValue(), required, caller.userId());
        else s.set(required, caller.userId());
        settingRepository.save(s);
        if (was != required)
            audit(tenantId, "SETTING", tenantId.getValue(), "Price approval", CatalogueAuditAction.SETTING_CHANGED,
                    "Price changes " + (required ? "now need approval" : "no longer need approval"), caller.userId());
        return new CatalogueSettingsResponse(required, s.isItemApprovalRequired(), s.getUpdatedAt());
    }

    // ── Price lists ───────────────────────────────────────────────────────────

    @Transactional
    public List<CataloguePriceListResponse> lists(TenantId tenantId) {
        ensureDefaultList(tenantId);
        return listRepository.findAllLive(tenantId).stream().map(CataloguePricingService::toResponse).toList();
    }

    @Transactional
    public CataloguePriceListResponse createList(TenantId tenantId, CataloguePriceListRequest r, Caller caller) {
        ensureDefaultList(tenantId);
        String code = PricingRules.listCode(r.code());
        String name = PricingRules.listName(r.name());
        if (listRepository.codeTaken(tenantId, code)) throw new ConflictException("A price list with the code '" + code + "' already exists.");
        if (listRepository.nameTaken(tenantId, name, new UUID(0L, 0L))) throw new ConflictException("A price list named '" + name + "' already exists.");
        var l = CataloguePriceList.create(tenantId, code, name, false, r.sortOrder() == null ? 10 : r.sortOrder());
        listRepository.save(l);
        audit(tenantId, "PRICE_LIST", l.getId(), l.getName(), CatalogueAuditAction.CREATED, "Created price list " + code, caller.userId());
        return toResponse(l);
    }

    @Transactional
    public CataloguePriceListResponse updateList(TenantId tenantId, UUID id, CataloguePriceListRequest r, Caller caller) {
        var l = listRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("PriceList", id.toString()));
        String name = PricingRules.listName(r.name());
        boolean active = r.active() == null || r.active();
        if (l.isDefaultList() && !active) throw new IllegalArgumentException("The default price list cannot be switched off.");
        if (listRepository.nameTaken(tenantId, name, l.getId())) throw new ConflictException("A price list named '" + name + "' already exists.");
        l.update(name, r.sortOrder() == null ? l.getSortOrder() : r.sortOrder(), active);
        listRepository.save(l);
        audit(tenantId, "PRICE_LIST", l.getId(), l.getName(), CatalogueAuditAction.UPDATED, "Updated" + (active ? "" : " (switched off)"), caller.userId());
        return toResponse(l);
    }

    @Transactional
    public void deleteList(TenantId tenantId, UUID id, Caller caller) {
        var l = listRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("PriceList", id.toString()));
        if (l.isDefaultList()) throw new IllegalArgumentException("The default price list cannot be deleted.");
        long n = changeRepository.countForList(tenantId, l.getId());
        if (n > 0) throw new ConflictException("This price list has " + n + (n == 1 ? " price change" : " price changes") + " on record. Switch it off instead.");
        l.softDelete(caller.userId());
        listRepository.save(l);
        audit(tenantId, "PRICE_LIST", l.getId(), l.getName(), CatalogueAuditAction.DELETED, "Deleted", caller.userId());
    }

    // ── Reading an item's prices ──────────────────────────────────────────────

    @Transactional
    public ItemPricingResponse pricing(TenantId tenantId, UUID itemId, Caller caller) {
        var item = item(tenantId, itemId);
        ensureDefaultList(tenantId);
        var lists = listRepository.findAllLive(tenantId).stream().filter(CataloguePriceList::isActive).toList();
        Map<UUID, BigDecimal> current = currentPrices(tenantId, item, lists);
        Map<UUID, List<CataloguePriceChange>> open = new HashMap<>();
        for (var c : changeRepository.openForItem(tenantId, itemId)) open.computeIfAbsent(c.getPriceListId(), k -> new ArrayList<>()).add(c);
        Map<UUID, String> names = new HashMap<>();
        lists.forEach(l -> names.put(l.getId(), l.getName()));
        var rows = new ArrayList<ItemPricingResponse.ListPrice>();
        for (var l : lists) {
            BigDecimal price = current.get(l.getId());
            rows.add(new ItemPricingResponse.ListPrice(l.getId(), l.getCode(), l.getName(), l.isDefaultList(), price,
                    PricingRules.withVat(price, item.getVatRate(), item.isVatExempt()),
                    PricingRules.marginPercent(price, item.getCostPrice()),
                    open.getOrDefault(l.getId(), List.of()).stream().map(c -> toResponse(c, item.getName(), names.get(c.getPriceListId()), caller)).toList()));
        }
        return new ItemPricingResponse(item.getId(), item.getName(), item.getUnit(), item.getMinPrice(), item.getCostPrice(),
                item.getVatRate(), item.isVatExempt(), approvalRequired(tenantId), rows);
    }

    @Transactional(readOnly = true)
    public List<PriceChangeResponse> itemHistory(TenantId tenantId, UUID itemId, int size, Caller caller) {
        var item = item(tenantId, itemId);
        Map<UUID, String> names = listNames(tenantId);
        return changeRepository.history(tenantId, itemId, PageRequest.of(0, Math.min(Math.max(1, size), 100))).stream()
                .map(c -> toResponse(c, item.getName(), names.get(c.getPriceListId()), caller)).toList();
    }

    /** The approval queue (PENDING) or the schedule (SCHEDULED) of the whole tenant. */
    @Transactional(readOnly = true)
    public List<PriceChangeResponse> queue(TenantId tenantId, PriceChangeStatus status, Caller caller) {
        Map<UUID, String> names = listNames(tenantId);
        var changes = changeRepository.byStatus(tenantId, status, PageRequest.of(0, 200));
        Map<UUID, String> itemNames = new HashMap<>();
        itemRepository.findAllById(changes.stream().map(CataloguePriceChange::getItemId).distinct().toList()).forEach(i -> itemNames.put(i.getId(), i.getName()));
        return changes.stream().map(c -> toResponse(c, itemNames.get(c.getItemId()), names.get(c.getPriceListId()), caller)).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount(TenantId tenantId) { return changeRepository.countByStatus(tenantId, PriceChangeStatus.PENDING); }

    // ── Changing prices ───────────────────────────────────────────────────────

    /**
     * Proposes a new price. When the tenant needs approval it waits as PENDING; otherwise it is decided on the spot
     * (applied today, or scheduled for its date).
     */
    @Transactional
    public PriceChangeResponse propose(TenantId tenantId, UUID itemId, PriceChangeRequest r, Caller caller) {
        requirePriceWriter(caller);
        var item = item(tenantId, itemId);
        if (item.getStatus() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be re-priced. Restore it to a draft first.");
        var list = listRepository.findLiveById(tenantId, r.priceListId()).orElseThrow(() -> new ResourceNotFoundException("PriceList", r.priceListId().toString()));
        if (!list.isActive()) throw new IllegalArgumentException("That price list is switched off.");
        BigDecimal price = CatalogueRules.checkMoney("The price", r.newPrice());
        PricingRules.checkAboveMinimum(price, item.getMinPrice());
        LocalDate from = PricingRules.effectiveDate(r.effectiveFrom(), today());
        boolean needsApproval = approvalRequired(tenantId);
        String reason = PricingRules.reason(r.reason(), needsApproval);
        BigDecimal oldPrice = currentPrice(tenantId, item, list);
        if (oldPrice != null && oldPrice.compareTo(price) == 0 && !from.isAfter(today())) throw new IllegalArgumentException("That is already the price.");
        if (changeRepository.pendingExists(tenantId, itemId, list.getId()))
            throw new ConflictException("A price change for " + list.getName() + " is already waiting for a decision. Decide or cancel it first.");

        var change = CataloguePriceChange.propose(tenantId, itemId, list.getId(), oldPrice, price, from, reason, caller.userId());
        String line = PricingRules.describe(list.getName(), oldPrice, price, from);
        if (needsApproval) {
            changeRepository.save(change);
            audit(tenantId, item, CatalogueAuditAction.PRICE_PROPOSED, line + (reason == null ? "" : ". " + reason), caller.userId());
        } else {
            change.approve(caller.userId(), "No approval needed", today());
            changeRepository.save(change);
            audit(tenantId, item, CatalogueAuditAction.PRICE_PROPOSED, line + (reason == null ? "" : ". " + reason), caller.userId());
            if (change.getStatus() == PriceChangeStatus.APPLIED) applyToItem(item, list, change, caller.userId());
        }
        return toResponse(change, item.getName(), list.getName(), caller);
    }

    @Transactional
    public PriceChangeResponse approve(TenantId tenantId, UUID changeId, PriceDecisionRequest r, Caller caller) {
        if (!caller.canApprovePrices()) throw new AccessDeniedException("You are not allowed to approve price changes.");
        var change = pending(tenantId, changeId);
        try { PricingRules.checkMakerChecker(change.getProposedBy(), caller.userId()); }
        catch (PricingRules.SelfApprovalException e) { throw new AccessDeniedException(e.getMessage()); }
        var item = item(tenantId, change.getItemId());
        var list = listRepository.findLiveById(tenantId, change.getPriceListId()).orElseThrow(() -> new ResourceNotFoundException("PriceList", change.getPriceListId().toString()));
        PricingRules.checkAboveMinimum(change.getNewPrice(), item.getMinPrice());
        LocalDate today = today();
        if (change.getEffectiveFrom().isBefore(today))
            throw new IllegalStateException("The effective date " + change.getEffectiveFrom() + " has passed. Reject this change and propose it again with a new date.");
        change.approve(caller.userId(), PricingRules.note(r == null ? null : r.note(), false), today);
        changeRepository.save(change);
        audit(tenantId, item, CatalogueAuditAction.PRICE_APPROVED, PricingRules.describe(list.getName(), change.getOldPrice(), change.getNewPrice(), change.getEffectiveFrom()), caller.userId());
        if (change.getStatus() == PriceChangeStatus.APPLIED) applyToItem(item, list, change, caller.userId());
        return toResponse(change, item.getName(), list.getName(), caller);
    }

    @Transactional
    public PriceChangeResponse reject(TenantId tenantId, UUID changeId, PriceDecisionRequest r, Caller caller) {
        if (!caller.canApprovePrices()) throw new AccessDeniedException("You are not allowed to decide price changes.");
        var change = pending(tenantId, changeId);
        try { PricingRules.checkMakerChecker(change.getProposedBy(), caller.userId()); }
        catch (PricingRules.SelfApprovalException e) { throw new AccessDeniedException(e.getMessage()); }
        String note = PricingRules.note(r == null ? null : r.note(), true);
        var item = item(tenantId, change.getItemId());
        change.reject(caller.userId(), note);
        changeRepository.save(change);
        String listName = listNames(tenantId).get(change.getPriceListId());
        audit(tenantId, item, CatalogueAuditAction.PRICE_REJECTED, PricingRules.describe(listName, change.getOldPrice(), change.getNewPrice(), change.getEffectiveFrom()) + ". " + note, caller.userId());
        return toResponse(change, item.getName(), listName, caller);
    }

    /** The proposer may withdraw their own change; an approver may cancel any open one (pending or scheduled). */
    @Transactional
    public PriceChangeResponse cancel(TenantId tenantId, UUID changeId, PriceDecisionRequest r, Caller caller) {
        var change = changeRepository.findOwned(tenantId, changeId).orElseThrow(() -> new ResourceNotFoundException("PriceChange", changeId.toString()));
        if (!change.getStatus().open()) throw new IllegalStateException("This price change has already been " + change.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        boolean mine = caller.userId() != null && caller.userId().equals(change.getProposedBy());
        if (!mine && !caller.canApprovePrices()) throw new AccessDeniedException("Only the person who proposed this change, or an approver, can cancel it.");
        var item = item(tenantId, change.getItemId());
        change.cancel(caller.userId(), PricingRules.note(r == null ? null : r.note(), false));
        changeRepository.save(change);
        String listName = listNames(tenantId).get(change.getPriceListId());
        audit(tenantId, item, CatalogueAuditAction.PRICE_CANCELLED, PricingRules.describe(listName, change.getOldPrice(), change.getNewPrice(), change.getEffectiveFrom()), caller.userId());
        return toResponse(change, item.getName(), listName, caller);
    }

    @Transactional
    public ItemPricingResponse setMinPrice(TenantId tenantId, UUID itemId, MinPriceRequest r, Caller caller) {
        requirePriceWriter(caller);
        var item = item(tenantId, itemId);
        BigDecimal min = CatalogueRules.checkMoney("The minimum price", r.minPrice());
        if (min != null && item.getDefaultPrice() != null && item.getDefaultPrice().compareTo(min) < 0)
            throw new IllegalArgumentException("The current price (" + PricingRules.money(item.getDefaultPrice()) + ") is already below that minimum. Change the price first.");
        BigDecimal before = item.getMinPrice();
        if ((before == null) == (min == null) && (before == null || before.compareTo(min) == 0)) return pricing(tenantId, itemId, caller);
        item.changeMinPrice(min);
        itemRepository.save(item);
        audit(tenantId, item, CatalogueAuditAction.UPDATED, "minimum price: " + PricingRules.money(before) + " -> " + PricingRules.money(min), caller.userId());
        return pricing(tenantId, itemId, caller);
    }

    /**
     * A price edited straight on the item form (allowed only when the tenant needs no approval) still leaves a history
     * line, so the price history is complete whichever way the price changed.
     */
    @Transactional
    public void recordDirectChange(TenantId tenantId, CatalogueItem item, BigDecimal oldPrice, BigDecimal newPrice, Caller caller) {
        PricingRules.checkAboveMinimum(newPrice, item.getMinPrice());
        var list = ensureDefaultList(tenantId);
        var change = CataloguePriceChange.propose(tenantId, item.getId(), list.getId(), oldPrice, newPrice, today(), null, caller.userId());
        change.approve(caller.userId(), "Edited on the item", today());
        changeRepository.save(change);
    }

    /** Whether a price edit on the item form is allowed now. */
    public void requireDirectEditAllowed(TenantId tenantId) {
        if (approvalRequired(tenantId))
            throw new IllegalArgumentException("Prices on this account change through a price change request, which needs approval. Use Change price on the item.");
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    /** Puts every scheduled change whose day has come into force. Safe to run any number of times. Returns how many were applied. */
    @Transactional
    public int applyDue() {
        LocalDate today = today();
        int applied = 0;
        for (var change : changeRepository.due(today)) {
            var item = itemRepository.findActiveById(change.getTenantId(), change.getItemId()).orElse(null);
            var list = listRepository.findLiveById(change.getTenantId(), change.getPriceListId()).orElse(null);
            if (item == null || list == null) {
                change.cancel(null, "Not applied: the item or price list no longer exists");
                changeRepository.save(change);
                continue;
            }
            if (item.getMinPrice() != null && change.getNewPrice().compareTo(item.getMinPrice()) < 0) {
                change.cancel(null, "Not applied: below the minimum price of " + PricingRules.money(item.getMinPrice()));
                changeRepository.save(change);
                audit(change.getTenantId(), item, CatalogueAuditAction.PRICE_CANCELLED,
                        PricingRules.describe(list.getName(), change.getOldPrice(), change.getNewPrice(), change.getEffectiveFrom()) + ". Not applied: below the minimum price", null);
                continue;
            }
            change.markApplied();
            changeRepository.save(change);
            applyToItem(item, list, change, null);
            applied++;
        }
        if (applied > 0) log.info("Applied {} scheduled catalogue price change(s) for {}", applied, today);
        return applied;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Puts an applied change into force on the item: the default list is the item's own price. */
    private void applyToItem(CatalogueItem item, CataloguePriceList list, CataloguePriceChange change, UUID actor) {
        if (list.isDefaultList()) {
            item.changeDefaultPrice(change.getNewPrice());
            itemRepository.save(item);
        }
        audit(item.getTenantId(), item, CatalogueAuditAction.PRICE_CHANGED,
                PricingRules.describe(list.getName(), change.getOldPrice(), change.getNewPrice(), change.getEffectiveFrom()), actor);
    }

    /** The id of the default (Retail) list, created if the tenant has none yet. */
    @Transactional
    public UUID defaultListId(TenantId tenantId) { return ensureDefaultList(tenantId).getId(); }

    private CataloguePriceList ensureDefaultList(TenantId tenantId) {
        return listRepository.findDefault(tenantId).orElseGet(() -> listRepository.save(CataloguePriceList.create(tenantId, "RETAIL", "Retail", true, 0)));
    }

    private BigDecimal currentPrice(TenantId tenantId, CatalogueItem item, CataloguePriceList list) {
        if (list.isDefaultList()) return item.getDefaultPrice();
        return currentPrices(tenantId, item, List.of(list)).get(list.getId());
    }

    /** The default list reads the item's price; other lists read their latest applied change. */
    /**
     * The price of each item on one list, for a channel's feed. A null or default list means the item's own selling price;
     * any other list has a price only where a change has been applied to it. Items with no price are left out of the map.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> pricesOnList(TenantId tenantId, UUID listId, Collection<CatalogueItem> items) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (items.isEmpty()) return out;
        var list = listId == null ? null : listRepository.findLiveById(tenantId, listId).orElse(null);
        if (list == null || list.isDefaultList()) {
            for (var i : items) if (i.getDefaultPrice() != null) out.put(i.getId(), i.getDefaultPrice());
            return out;
        }
        for (var c : changeRepository.appliedOnList(tenantId, list.getId(), items.stream().map(CatalogueItem::getId).toList())) {
            out.putIfAbsent(c.getItemId(), c.getNewPrice());   // latest first, so the first one seen wins
        }
        return out;
    }

    private Map<UUID, BigDecimal> currentPrices(TenantId tenantId, CatalogueItem item, List<CataloguePriceList> lists) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        var applied = changeRepository.appliedForItem(tenantId, item.getId());
        for (var l : lists) {
            if (l.isDefaultList()) { out.put(l.getId(), item.getDefaultPrice()); continue; }
            applied.stream().filter(c -> c.getPriceListId().equals(l.getId())).findFirst().ifPresent(c -> out.put(l.getId(), c.getNewPrice()));
        }
        return out;
    }

    private CataloguePriceChange pending(TenantId tenantId, UUID id) {
        var c = changeRepository.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("PriceChange", id.toString()));
        if (c.getStatus() != PriceChangeStatus.PENDING) throw new IllegalStateException("This price change is no longer waiting for a decision (" + c.getStatus().name().toLowerCase(Locale.ROOT) + ").");
        return c;
    }

    private CatalogueItem item(TenantId tenantId, UUID id) {
        return itemRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", id.toString()));
    }

    private Map<UUID, String> listNames(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        listRepository.findAllLive(tenantId).forEach(l -> m.put(l.getId(), l.getName()));
        return m;
    }

    private static void requirePriceWriter(Caller caller) {
        if (!caller.canWritePrices()) throw new AccessDeniedException("You are not allowed to change prices.");
    }

    private void audit(TenantId tenantId, CatalogueItem item, CatalogueAuditAction action, String detail, UUID actor) {
        audit(tenantId, "ITEM", item.getId(), item.getName(), action, detail, actor);
    }

    private void audit(TenantId tenantId, String type, UUID id, String name, CatalogueAuditAction action, String detail, UUID actor) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, type, id, name, action, detail, actor));
    }

    private static CataloguePriceListResponse toResponse(CataloguePriceList l) {
        return new CataloguePriceListResponse(l.getId(), l.getCode(), l.getName(), l.isDefaultList(), l.isActive(), l.getSortOrder());
    }

    private static PriceChangeResponse toResponse(CataloguePriceChange c, String itemName, String listName, Caller caller) {
        return new PriceChangeResponse(c.getId(), c.getItemId(), itemName, c.getPriceListId(), listName, c.getOldPrice(), c.getNewPrice(),
                PricingRules.changePercent(c.getOldPrice(), c.getNewPrice()), c.getEffectiveFrom(), c.getReason(), c.getStatus().name(),
                caller.userId() != null && caller.userId().equals(c.getProposedBy()), c.getProposedAt(), c.getDecidedAt(), c.getDecisionNote(), c.getAppliedAt());
    }
}
