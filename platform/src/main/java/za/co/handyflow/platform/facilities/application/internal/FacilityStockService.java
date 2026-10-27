package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrder;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockLevel;
import za.co.handyflow.platform.facilities.domain.model.FacilityStore;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockMovement;
import za.co.handyflow.platform.facilities.domain.repository.FacilityPurchaseOrderRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockItemRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockLevelRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStoreRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockMovementRepository;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock items and the movements ledger.
 * <ul>
 *   <li>Every change to quantity on hand re-reads the item under a row lock, so concurrent issues cannot take
 *       the same units twice, and writes a ledger line carrying the balance afterwards.</li>
 *   <li>Stock cannot go negative (409 {@code INSUFFICIENT_STOCK}).</li>
 *   <li>Issuing to a work order goes through {@link FacilityWorkOrderCostService}, so it obeys the same access
 *       scope (technicians: own jobs) and the same rule that costs are frozen once the job is complete; it adds
 *       a PARTS cost line at the current average cost.</li>
 *   <li>Falling to or below the reorder level raises one alert as it crosses, not on every later issue.</li>
 *   <li>Receipts, issues, returns and stocktake adjustments are all audited.</li>
 * </ul>
 * Removing a cost line does not put the parts back; use a return.
 * <p>
 * Stock is held per store. The item keeps its total on hand and one average cost; the per-store levels always add up
 * to the total. Every movement names a store (the default store when none is given), an issue can only take what that
 * store holds, a stocktake counts one store, and a transfer moves stock between stores without changing the total.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityStockService {

    private final FacilityStockItemRepository itemRepository;
    private final FacilityStockMovementRepository movementRepository;
    private final FacilityPurchaseOrderRepository purchaseOrderRepository;
    private final FacilityWorkOrderCostService costService;
    private final FacilityAuditService auditService;
    private final FacilityAlerts alerts;
    private final FacilityStoreRepository storeRepository;
    private final FacilityStockLevelRepository levelRepository;

    // ── Items ───────────────────────────────────────────────────────────────────────────────

    @Transactional
    public StockItemResponse createItem(TenantId tenantId, UpsertStockItemRequest req) {
        String sku = FacilityStockItem.normaliseSku(req.sku());
        if (itemRepository.skuExists(tenantId, sku))
            throw new HandyFlowException("SKU " + sku + " already exists", HttpStatus.CONFLICT, "SKU_TAKEN");
        FacilityStockItem item = FacilityStockItem.create(tenantId, sku, req.name(), req.unit(), req.location(),
                req.reorderLevel(), req.reorderQty());
        itemRepository.save(item);
        auditService.record(tenantId, "STOCK_ITEM_CREATED", "STOCK_ITEM", item.getId(), sku + " " + item.getName());

        BigDecimal opening = req.openingQuantity();
        if (opening != null && opening.signum() > 0) {
            BigDecimal cost = req.openingUnitCost() == null ? BigDecimal.ZERO : req.openingUnitCost();
            FacilityStore store = resolveStore(tenantId, null);
            item.receive(opening, cost);
            FacilityStockLevel openingLevel = level(tenantId, store, item);
            openingLevel.add(opening);
            levelRepository.save(openingLevel);
            movementRepository.save(FacilityStockMovement.of(tenantId, item.getId(), store.getId(), "RECEIPT", opening, cost,
                    item.getOnHand(), null, null, null, "Opening balance", TenantContext.getCurrentUserId()));
            auditService.record(tenantId, "STOCK_RECEIVED", "STOCK_ITEM", item.getId(), sku + " +" + opening + " opening balance");
        }
        return toResponse(item);
    }

    @Transactional
    public StockItemResponse updateItem(TenantId tenantId, UUID id, UpsertStockItemRequest req) {
        FacilityStockItem item = find(tenantId, id);
        item.update(req.name(), req.unit(), req.location(), req.reorderLevel(), req.reorderQty());
        itemRepository.save(item);
        auditService.record(tenantId, "STOCK_ITEM_UPDATED", "STOCK_ITEM", id, item.getSku() + " " + item.getName());
        return toResponse(item);
    }

    @Transactional
    public StockItemResponse setActive(TenantId tenantId, UUID id, boolean active) {
        FacilityStockItem item = find(tenantId, id);
        if (active) item.reactivate(); else item.deactivate();
        itemRepository.save(item);
        auditService.record(tenantId, active ? "STOCK_ITEM_REACTIVATED" : "STOCK_ITEM_DEACTIVATED", "STOCK_ITEM", id, item.getSku());
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> list(TenantId tenantId, String q, boolean includeInactive, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase() + "%";
        return itemRepository.search(tenantId, !includeInactive, pattern, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StockItemResponse get(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(TenantId tenantId, UUID itemId, Pageable pageable) {
        find(tenantId, itemId);
        return movementRepository.findForItem(tenantId, itemId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestionResponse> reorderSuggestions(TenantId tenantId) {
        Map<UUID, BigDecimal> onOrder = new HashMap<>();
        for (FacilityPurchaseOrder po : purchaseOrderRepository.findOpen(tenantId))
            po.getLines().forEach(l -> onOrder.merge(l.getItemId(), l.outstanding(), BigDecimal::add));
        return FacilityReorderRules.suggest(itemRepository.findBelowReorderLevel(tenantId), onOrder);
    }

    // ── Movements ───────────────────────────────────────────────────────────────────────────

    /** Stock bought outside a purchase order (petty-cash purchase, donation). {@code storeId} null means the default store. */
    @Transactional
    public StockItemResponse receive(TenantId tenantId, UUID itemId, ReceiveStockRequest req) {
        return receive(tenantId, itemId, req, null);
    }

    @Transactional
    public StockItemResponse receive(TenantId tenantId, UUID itemId, ReceiveStockRequest req, UUID storeId) {
        FacilityStockItem item = lockActive(tenantId, itemId);
        book(tenantId, item, resolveStore(tenantId, storeId), "RECEIPT", req.quantity(), req.unitCost(), null, null, null, req.reference());
        return toResponse(item);
    }

    /** A delivery against a purchase order line; called by the purchase order service inside its transaction. */
    @Transactional
    public void receiveAgainstOrder(TenantId tenantId, UUID itemId, BigDecimal quantity, BigDecimal unitCost,
                                    UUID purchaseOrderId, String reference) {
        receiveAgainstOrder(tenantId, itemId, quantity, unitCost, purchaseOrderId, reference, null);
    }

    @Transactional
    public void receiveAgainstOrder(TenantId tenantId, UUID itemId, BigDecimal quantity, BigDecimal unitCost,
                                    UUID purchaseOrderId, String reference, UUID storeId) {
        FacilityStockItem item = lockForUpdate(tenantId, itemId);
        book(tenantId, item, resolveStore(tenantId, storeId), "RECEIPT", quantity, unitCost, null, null, purchaseOrderId, reference);
    }

    @Transactional
    public StockItemResponse issue(TenantId tenantId, UUID itemId, IssueStockRequest req) {
        return issue(tenantId, itemId, req, null);
    }

    @Transactional
    public StockItemResponse issue(TenantId tenantId, UUID itemId, IssueStockRequest req, UUID storeId) {
        FacilityStockItem item = lockActive(tenantId, itemId);
        FacilityStore store = resolveStore(tenantId, storeId);
        if (req.quantity().compareTo(item.getOnHand()) > 0) throw insufficient(item);
        if (req.quantity().compareTo(level(tenantId, store, item).getOnHand()) > 0) throw insufficientInStore(item, store, level(tenantId, store, item));
        // Validates the work order (scope, status) and records the cost; rolls back with us if anything below fails.
        var line = costService.add(tenantId, req.workOrderId(), new AddCostLineRequest("PARTS",
                item.getSku() + " " + item.getName(), req.quantity(), item.getUnitCost()));
        book(tenantId, item, store, "ISSUE", req.quantity(), item.getUnitCost(), req.workOrderId(), line.id(), null, req.note());
        return toResponse(item);
    }

    @Transactional
    public StockItemResponse returnStock(TenantId tenantId, UUID itemId, ReturnStockRequest req) {
        return returnStock(tenantId, itemId, req, null);
    }

    @Transactional
    public StockItemResponse returnStock(TenantId tenantId, UUID itemId, ReturnStockRequest req, UUID storeId) {
        FacilityStockItem item = lockActive(tenantId, itemId);
        book(tenantId, item, resolveStore(tenantId, storeId), "RETURN", req.quantity(), item.getUnitCost(), req.workOrderId(), null, null, req.reason());
        return toResponse(item);
    }

    /** Stocktake of one store: its level is set to the counted figure and the item's total moves by the same difference. */
    @Transactional
    public StockItemResponse adjust(TenantId tenantId, UUID itemId, AdjustStockRequest req) {
        return adjust(tenantId, itemId, req, null);
    }

    @Transactional
    public StockItemResponse adjust(TenantId tenantId, UUID itemId, AdjustStockRequest req, UUID storeId) {
        FacilityStockItem item = lockForUpdate(tenantId, itemId);
        FacilityStore store = resolveStore(tenantId, storeId);
        boolean wasLow = item.isBelowReorderLevel();
        FacilityStockLevel level = level(tenantId, store, item);
        BigDecimal diff = level.count(req.countedQuantity());
        if (diff.signum() == 0) return toResponse(item);
        item.count(item.getOnHand().add(diff));
        levelRepository.save(level);
        record(tenantId, item, store, "ADJUSTMENT", diff, item.getUnitCost(), null, null, null, req.reason());
        afterChange(tenantId, item, wasLow);
        return toResponse(item);
    }

    /** Move stock between two stores. The item's total and average cost do not change. */
    @Transactional
    public StockItemResponse transfer(TenantId tenantId, UUID itemId, TransferStockRequest req) {
        FacilityStockItem item = lockActive(tenantId, itemId);
        if (req.fromStoreId().equals(req.toStoreId())) throw new IllegalArgumentException("Choose two different stores");
        FacilityStore from = resolveStore(tenantId, req.fromStoreId());
        FacilityStore to = resolveStore(tenantId, req.toStoreId());
        FacilityStockLevel source = level(tenantId, from, item);
        if (req.quantity().compareTo(source.getOnHand()) > 0) throw insufficientInStore(item, from, source);
        FacilityStockLevel target = level(tenantId, to, item);
        source.remove(req.quantity());
        target.add(req.quantity());
        levelRepository.save(source);
        levelRepository.save(target);
        String reference = "Transfer " + from.getCode() + " -> " + to.getCode() + (req.reason() == null || req.reason().isBlank() ? "" : " · " + req.reason().trim());
        UUID by = TenantContext.getCurrentUserId();
        movementRepository.save(FacilityStockMovement.of(tenantId, item.getId(), from.getId(), "TRANSFER_OUT", req.quantity().negate(),
                item.getUnitCost(), item.getOnHand(), null, null, null, reference, by));
        movementRepository.save(FacilityStockMovement.of(tenantId, item.getId(), to.getId(), "TRANSFER_IN", req.quantity(),
                item.getUnitCost(), item.getOnHand(), null, null, null, reference, by));
        auditService.record(tenantId, "STOCK_TRANSFERRED", "STOCK_ITEM", item.getId(),
                item.getSku() + " " + req.quantity().stripTrailingZeros().toPlainString() + " " + from.getCode() + " -> " + to.getCode());
        return toResponse(item);
    }

    /** What each store holds of this item. */
    @Transactional(readOnly = true)
    public List<StockLevelResponse> levels(TenantId tenantId, UUID itemId) {
        find(tenantId, itemId);
        Map<UUID, FacilityStore> stores = new HashMap<>();
        storeRepository.findAllForTenant(tenantId).forEach(s -> stores.put(s.getId(), s));
        return levelRepository.findForItem(tenantId, itemId).stream()
                .filter(l -> stores.containsKey(l.getStoreId()))
                .map(l -> new StockLevelResponse(l.getStoreId(), stores.get(l.getStoreId()).getCode(), stores.get(l.getStoreId()).getName(), l.getOnHand()))
                .sorted(java.util.Comparator.comparing(StockLevelResponse::storeName)).toList();
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────

    /** The named store (must be active), or the tenant's default, created on first use if there is none yet. */
    FacilityStore resolveStore(TenantId tenantId, UUID storeId) {
        if (storeId != null) {
            FacilityStore s = storeRepository.findByIdForTenant(tenantId, storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityStore", storeId.toString()));
            if (!s.isActive())
                throw new HandyFlowException(s.getName() + " is inactive", HttpStatus.CONFLICT, "STORE_INACTIVE");
            return s;
        }
        return storeRepository.findDefault(tenantId).orElseGet(() -> storeRepository.save(FacilityStore.create(tenantId, "MAIN", "Main store", null, true)));
    }

    private FacilityStockLevel level(TenantId tenantId, FacilityStore store, FacilityStockItem item) {
        return levelRepository.findForStoreItem(tenantId, store.getId(), item.getId())
                .orElseGet(() -> FacilityStockLevel.create(tenantId, store.getId(), item.getId()));
    }

    private void book(TenantId tenantId, FacilityStockItem item, FacilityStore store, String type, BigDecimal qty, BigDecimal cost,
                      UUID workOrderId, UUID costLineId, UUID purchaseOrderId, String reference) {
        boolean wasLow = item.isBelowReorderLevel();
        FacilityStockLevel level = level(tenantId, store, item);
        BigDecimal signed;
        switch (type) {
            case "RECEIPT" -> { item.receive(qty, cost); level.add(qty); signed = qty; }
            case "RETURN" -> { item.putBack(qty); level.add(qty); signed = qty; }
            case "ISSUE" -> {
                if (qty.compareTo(item.getOnHand()) > 0) throw insufficient(item);
                if (qty.compareTo(level.getOnHand()) > 0) throw insufficientInStore(item, store, level);
                item.issue(qty);
                level.remove(qty);
                signed = qty.negate();
            }
            default -> throw new IllegalArgumentException("Unsupported movement " + type);
        }
        levelRepository.save(level);
        record(tenantId, item, store, type, signed, cost, workOrderId, costLineId, purchaseOrderId, reference);
        afterChange(tenantId, item, wasLow);
    }

    private void record(TenantId tenantId, FacilityStockItem item, FacilityStore store, String type, BigDecimal signed, BigDecimal cost,
                        UUID workOrderId, UUID costLineId, UUID purchaseOrderId, String reference) {
        itemRepository.save(item);
        movementRepository.save(FacilityStockMovement.of(tenantId, item.getId(), store.getId(), type, signed, cost, item.getOnHand(),
                workOrderId, costLineId, purchaseOrderId, reference, TenantContext.getCurrentUserId()));
        auditService.record(tenantId, auditAction(type),
                "STOCK_ITEM", item.getId(), item.getSku() + " " + (signed.signum() >= 0 ? "+" : "") + signed.stripTrailingZeros().toPlainString()
                        + " balance=" + item.getOnHand().stripTrailingZeros().toPlainString() + " store=" + store.getCode()
                        + (reference == null || reference.isBlank() ? "" : " (" + reference.trim() + ")"));
    }

    private static String auditAction(String type) {
        return switch (type) {
            case "RECEIPT" -> "STOCK_RECEIVED";
            case "ISSUE" -> "STOCK_ISSUED";
            case "RETURN" -> "STOCK_RETURNED";
            default -> "STOCK_ADJUSTED";
        };
    }

    private void afterChange(TenantId tenantId, FacilityStockItem item, boolean wasLow) {
        if (!wasLow && item.isBelowReorderLevel()) {
            alerts.toAdmins(tenantId, NotificationType.FACILITY_STOCK_LOW,
                    "Low stock: " + item.getName(),
                    item.getSku() + " is down to " + item.getOnHand().stripTrailingZeros().toPlainString() + " " + item.getUnit()
                            + " (reorder level " + item.getReorderLevel().stripTrailingZeros().toPlainString() + ")",
                    "/facilities/stock/" + item.getId(), item.getId().toString());
        }
    }

    private FacilityStockItem find(TenantId tenantId, UUID id) {
        return itemRepository.findForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityStockItem", id.toString()));
    }

    private FacilityStockItem lockForUpdate(TenantId tenantId, UUID id) {
        return itemRepository.findForUpdate(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityStockItem", id.toString()));
    }

    private FacilityStockItem lockActive(TenantId tenantId, UUID id) {
        FacilityStockItem item = lockForUpdate(tenantId, id);
        if (!item.isActive())
            throw new HandyFlowException(item.getSku() + " is inactive", HttpStatus.CONFLICT, "STOCK_ITEM_INACTIVE");
        return item;
    }

    private HandyFlowException insufficientInStore(FacilityStockItem item, FacilityStore store, FacilityStockLevel level) {
        return new HandyFlowException("Only " + level.getOnHand().stripTrailingZeros().toPlainString() + " " + item.getUnit() + " of "
                + item.getSku() + " in " + store.getName() + " (" + item.getOnHand().stripTrailingZeros().toPlainString()
                + " in total across stores). Transfer stock there first, or issue from another store", HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
    }

    private HandyFlowException insufficient(FacilityStockItem item) {
        return new HandyFlowException("Only " + item.getOnHand().stripTrailingZeros().toPlainString() + " " + item.getUnit()
                + " of " + item.getSku() + " on hand", HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
    }

    private StockItemResponse toResponse(FacilityStockItem i) {
        return new StockItemResponse(i.getId(), i.getSku(), i.getName(), i.getUnit(), i.getLocation(), i.getOnHand(),
                i.getReorderLevel(), i.getReorderQty(), i.getUnitCost(), i.stockValue(), i.isBelowReorderLevel(), i.isActive());
    }

    private StockMovementResponse toResponse(FacilityStockMovement m) {
        return new StockMovementResponse(m.getId(), m.getItemId(), m.getMovementType(), m.getQuantity(), m.getUnitCost(),
                m.getBalanceAfter(), m.getWorkOrderId(), m.getCostLineId(), m.getPurchaseOrderId(), m.getReference(),
                m.getCreatedByUserId(), m.getCreatedAt(), m.getStoreId());
    }
}
