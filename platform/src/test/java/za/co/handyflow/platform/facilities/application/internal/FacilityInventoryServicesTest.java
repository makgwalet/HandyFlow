package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Stock ledger, issuing to work orders, purchase orders and goods receipt, with in-memory repositories. */
class FacilityInventoryServicesTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID creator = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    private final Map<UUID, FacilityStockItem> items = new LinkedHashMap<>();
    private final List<FacilityStockMovement> movements = new ArrayList<>();
    private final Map<UUID, FacilityPurchaseOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, FacilityVendor> vendors = new HashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertTypes = new ArrayList<>();
    private final List<String> costLines = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>();
    private String workOrderError;
    private List<String> vendorProblems = List.of();
    private int seq;
    private int grnSeq;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> f) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = f.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityStockItemRepository itemRepo = fake(FacilityStockItemRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityStockItem i = (FacilityStockItem) a[0]; items.put(i.getId(), i); yield i; }
        case "findForTenant", "findForUpdate" -> Optional.ofNullable(items.get(a[1]));
        case "skuExists" -> items.values().stream().anyMatch(i -> i.getSku().equals(a[1]));
        case "findBelowReorderLevel" -> items.values().stream().filter(FacilityStockItem::isBelowReorderLevel).toList();
        case "findByIds" -> items.values().stream().filter(i -> ((Collection<?>) a[1]).contains(i.getId())).toList();
        default -> NOPE;
    });
    private final FacilityStockMovementRepository movementRepo = fake(FacilityStockMovementRepository.class, (n, a) -> {
        if ("save".equals(n)) { movements.add((FacilityStockMovement) a[0]); return a[0]; }
        return NOPE;
    });
    private final Map<UUID, FacilityStore> stores = new LinkedHashMap<>();
    private final List<FacilityStockLevel> levelList = new ArrayList<>();
    private final FacilityStoreRepository storeRepo = fake(FacilityStoreRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityStore st = (FacilityStore) a[0]; stores.put(st.getId(), st); yield st; }
        case "findDefault" -> stores.values().stream().filter(FacilityStore::isDefaultStore).findFirst();
        case "findByIdForTenant" -> Optional.ofNullable(stores.get(a[1]));
        case "findAllForTenant" -> new ArrayList<>(stores.values());
        default -> NOPE;
    });
    private final FacilityStockLevelRepository levelRepo = fake(FacilityStockLevelRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityStockLevel l = (FacilityStockLevel) a[0]; if (!levelList.contains(l)) levelList.add(l); yield l; }
        case "findForStoreItem" -> levelList.stream().filter(l -> l.getStoreId().equals(a[1]) && l.getItemId().equals(a[2])).findFirst();
        case "findForItem" -> levelList.stream().filter(l -> l.getItemId().equals(a[1])).toList();
        case "findAllPositive" -> levelList.stream().filter(l -> l.getOnHand().signum() > 0).toList();
        default -> NOPE;
    });
    private final FacilityPurchaseOrderRepository orderRepo = fake(FacilityPurchaseOrderRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityPurchaseOrder o = (FacilityPurchaseOrder) a[0]; orders.put(o.getId(), o); yield o; }
        case "findForTenant", "findForUpdate" -> Optional.ofNullable(orders.get(a[1]));
        case "findOpen" -> orders.values().stream().filter(FacilityPurchaseOrder::isOpen).toList();
        default -> NOPE;
    });
    private final List<FacilityGoodsReceipt> receipts = new ArrayList<>();
    private final Map<UUID, FacilityApprovalLimit> limits = new HashMap<>();
    private final FacilityGoodsReceiptRepository receiptRepo = fake(FacilityGoodsReceiptRepository.class, (n, a) -> switch (n) {
        case "save" -> { receipts.add((FacilityGoodsReceipt) a[0]); yield a[0]; }
        case "findForPurchaseOrder" -> receipts.stream().filter(r -> r.getPurchaseOrderId().equals(a[1])).toList();
        default -> NOPE;
    });
    private final FacilityApprovalLimitRepository limitRepo = fake(FacilityApprovalLimitRepository.class, (n, a) -> switch (n) {
        case "anyForTenant" -> !limits.isEmpty();
        case "findForUser" -> Optional.ofNullable(limits.get(a[1]));
        default -> NOPE;
    });
    private final FacilityVendorRepository vendorRepo = fake(FacilityVendorRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(vendors.get(a[1])) : NOPE);

    private final FacilityWorkOrderCostService costService = new FacilityWorkOrderCostService(null, null, null, null) {
        @Override public CostLineResponse add(TenantId t, UUID workOrderId, AddCostLineRequest r) {
            if (workOrderError != null) throw new IllegalStateException(workOrderError);
            costLines.add(workOrderId + "|" + r.costType() + "|" + r.quantity() + "|" + r.unitCost());
            return new CostLineResponse(UUID.randomUUID(), workOrderId, r.costType(), r.description(), r.quantity(), r.unitCost(),
                    r.quantity().multiply(r.unitCost()), creator, Instant.now());
        }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityAlerts alerts = new FacilityAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String src) { alertTypes.add(type); }
    };
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FacilityVendorEligibility eligibility = new FacilityVendorEligibility(null) {
        @Override public List<String> problems(TenantId t, FacilityVendor v) { return vendorProblems; }
    };
    private final FacilityNumberGenerator numbers = new FacilityNumberGenerator(null) {
        @Override public String nextPurchaseOrderNumber(TenantId t) { return "FPO-" + (++seq); }
        @Override public String nextGoodsReceiptNumber(TenantId t) { return "FGR-" + (++grnSeq); }
    };

    private final FacilityStockService stock = new FacilityStockService(itemRepo, movementRepo, orderRepo, costService, auditService, alerts, storeRepo, levelRepo);
    private final FacilityPurchaseOrderService po = new FacilityPurchaseOrderService(orderRepo, itemRepo, vendorRepo, eligibility, stock,
            numbers, policy, auditService, receiptRepo, limitRepo);

    private FacilityVendor vendor;

    FacilityInventoryServicesTest() {
        TenantContext.setUserId(creator.toString());
        vendor = FacilityVendor.create(tenant, "ACME Supplies", "PARTS", null, null, null, null);
        vendors.put(vendor.getId(), vendor);
    }

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private UUID newItem(String sku, String level, String qty, String opening, String cost) {
        StockItemResponse r = stock.createItem(tenant, new UpsertStockItemRequest(sku, "Item " + sku, "each", "Shelf 1",
                n(level), n(qty), opening == null ? null : n(opening), cost == null ? null : n(cost)));
        return r.id();
    }

    private PurchaseOrderRequest orderOf(UUID itemId, String qty, String cost) {
        return new PurchaseOrderRequest(vendor.getId(), null, null, List.of(new PurchaseOrderRequest.Line(itemId, n(qty), n(cost))));
    }

    // ── items and the ledger ────────────────────────────────────────────────────

    @Test
    void creatingAnItemWithOpeningStockBooksAReceipt() {
        UUID id = newItem("bolt-m8", "10", "100", "40", "2.50");
        assertEquals(0, n("40").compareTo(items.get(id).getOnHand()));
        assertEquals("BOLT-M8", items.get(id).getSku());
        assertEquals(1, movements.size());
        assertEquals("RECEIPT", movements.get(0).getMovementType());
        assertEquals(0, n("40").compareTo(movements.get(0).getBalanceAfter()));
        assertEquals(List.of("STOCK_ITEM_CREATED", "STOCK_RECEIVED"), audit);
    }

    @Test
    void aDuplicateSkuIsRefusedRegardlessOfCase() {
        newItem("A1", "0", "0", null, null);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> newItem("a1", "0", "0", null, null));
        assertEquals("SKU_TAKEN", e.getErrorCode());
    }

    @Test
    void issuingToAWorkOrderAddsAPartsCostLineAndALedgerEntry() {
        UUID id = newItem("A1", "0", "0", "10", "4.00");
        UUID wo = UUID.randomUUID();
        StockItemResponse r = stock.issue(tenant, id, new IssueStockRequest(wo, n("3"), "valve swap"));
        assertEquals(0, n("7").compareTo(r.onHand()));
        assertEquals(1, costLines.size());
        assertTrue(costLines.get(0).startsWith(wo + "|PARTS|3|4.00"));
        FacilityStockMovement m = movements.get(movements.size() - 1);
        assertEquals("ISSUE", m.getMovementType());
        assertEquals(0, n("-3").compareTo(m.getQuantity()));
        assertEquals(wo, m.getWorkOrderId());
        assertNotNull(m.getCostLineId());
        assertTrue(audit.contains("STOCK_ISSUED"));
    }

    @Test
    void youCannotIssueMoreThanIsOnHand() {
        UUID id = newItem("A1", "0", "0", "2", "4.00");
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> stock.issue(tenant, id, new IssueStockRequest(UUID.randomUUID(), n("3"), null)));
        assertEquals("INSUFFICIENT_STOCK", e.getErrorCode());
        assertEquals(0, n("2").compareTo(items.get(id).getOnHand()));
        assertTrue(costLines.isEmpty());
    }

    @Test
    void ifTheWorkOrderRejectsTheCostNothingIsDeducted() {
        UUID id = newItem("A1", "0", "0", "10", "4.00");
        int before = movements.size();
        workOrderError = "Costs can only be changed while the work order is ASSIGNED, IN_PROGRESS or ON_HOLD";
        assertThrows(IllegalStateException.class, () -> stock.issue(tenant, id, new IssueStockRequest(UUID.randomUUID(), n("3"), null)));
        assertEquals(0, n("10").compareTo(items.get(id).getOnHand()));
        assertEquals(before, movements.size());
    }

    @Test
    void anInactiveItemCannotBeIssuedOrReceived() {
        UUID id = newItem("A1", "0", "0", "10", "4.00");
        stock.setActive(tenant, id, false);
        assertEquals("STOCK_ITEM_INACTIVE", assertThrows(HandyFlowException.class,
                () -> stock.issue(tenant, id, new IssueStockRequest(UUID.randomUUID(), n("1"), null))).getErrorCode());
        assertEquals("STOCK_ITEM_INACTIVE", assertThrows(HandyFlowException.class,
                () -> stock.receive(tenant, id, new ReceiveStockRequest(n("1"), n("1"), null))).getErrorCode());
    }

    @Test
    void returnsPutStockBackAtTheCurrentAverageCost() {
        UUID id = newItem("A1", "0", "0", "10", "4.00");
        stock.issue(tenant, id, new IssueStockRequest(UUID.randomUUID(), n("4"), null));
        StockItemResponse r = stock.returnStock(tenant, id, new ReturnStockRequest(null, n("1"), "unused"));
        assertEquals(0, n("7").compareTo(r.onHand()));
        assertEquals(0, n("4.00").compareTo(r.unitCost()));
        assertEquals("RETURN", movements.get(movements.size() - 1).getMovementType());
        assertTrue(audit.contains("STOCK_RETURNED"));
    }

    @Test
    void aStocktakeBooksTheDifferenceAndASameCountBooksNothing() {
        UUID id = newItem("A1", "0", "0", "10", "4.00");
        int before = movements.size();
        stock.adjust(tenant, id, new AdjustStockRequest(n("10"), "count"));
        assertEquals(before, movements.size());
        StockItemResponse r = stock.adjust(tenant, id, new AdjustStockRequest(n("8"), "two broken"));
        assertEquals(0, n("8").compareTo(r.onHand()));
        FacilityStockMovement m = movements.get(movements.size() - 1);
        assertEquals("ADJUSTMENT", m.getMovementType());
        assertEquals(0, n("-2").compareTo(m.getQuantity()));
        assertTrue(audit.contains("STOCK_ADJUSTED"));
    }

    @Test
    void theLowStockAlertFiresOnceAsTheLevelIsCrossed() {
        UUID id = newItem("A1", "5", "20", "8", "1.00");
        UUID wo = UUID.randomUUID();
        stock.issue(tenant, id, new IssueStockRequest(wo, n("2"), null));      // 6, still above
        assertTrue(alertTypes.isEmpty());
        stock.issue(tenant, id, new IssueStockRequest(wo, n("1"), null));      // 5, at level
        assertEquals(List.of(NotificationType.FACILITY_STOCK_LOW), alertTypes);
        stock.issue(tenant, id, new IssueStockRequest(wo, n("1"), null));      // 4, already low
        assertEquals(1, alertTypes.size());
        stock.receive(tenant, id, new ReceiveStockRequest(n("30"), n("1"), "restock"));
        stock.issue(tenant, id, new IssueStockRequest(wo, n("30"), null));     // crosses again
        assertEquals(2, alertTypes.size());
    }

    @Test
    void anUnknownItemReadsAsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> stock.get(tenant, UUID.randomUUID()));
    }

    @Test
    void reorderSuggestionsNetOffOpenOrders() {
        UUID id = newItem("A1", "10", "20", "4", "1.00");
        assertEquals(1, stock.reorderSuggestions(tenant).size());
        PurchaseOrderResponse draft = po.create(tenant, orderOf(id, "20", "1.00"));
        assertEquals(1, stock.reorderSuggestions(tenant).size());   // a draft does not count
        TenantContext.setUserId(approver.toString());
        po.approve(tenant, draft.id());
        assertTrue(stock.reorderSuggestions(tenant).isEmpty());
    }

    // ── purchase orders ─────────────────────────────────────────────────────────

    @Test
    void anOrderIsNumberedAndStartsAsADraft() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "10", "3.00"));
        assertEquals("FPO-1", r.poNumber());
        assertEquals("DRAFT", r.status());
        assertEquals(0, n("30.00").compareTo(r.totalValue()));
        assertEquals("ACME Supplies", r.vendorName());
        assertEquals(List.of("STOCK_ITEM_CREATED", "PO_CREATED"), audit);
    }

    @Test
    void anInactiveVendorCannotBeOrderedFrom() {
        UUID id = newItem("A1", "0", "0", null, null);
        vendor.deactivate();
        assertEquals("VENDOR_INACTIVE", assertThrows(HandyFlowException.class, () -> po.create(tenant, orderOf(id, "1", "1"))).getErrorCode());
    }

    @Test
    void anInactiveItemCannotBeOrdered() {
        UUID id = newItem("A1", "0", "0", null, null);
        stock.setActive(tenant, id, false);
        assertEquals("STOCK_ITEM_INACTIVE", assertThrows(HandyFlowException.class, () -> po.create(tenant, orderOf(id, "1", "1"))).getErrorCode());
    }

    @Test
    void theCreatorCannotApproveButAnotherPersonCan() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "1", "1"));
        assertEquals("PO_SELF_APPROVAL", assertThrows(HandyFlowException.class, () -> po.approve(tenant, r.id())).getErrorCode());
        TenantContext.setUserId(approver.toString());
        PurchaseOrderResponse ok = po.approve(tenant, r.id());
        assertEquals("APPROVED", ok.status());
        assertEquals(approver, ok.approvedByUserId());
        assertTrue(audit.contains("PO_APPROVED"));
    }

    @Test
    void anAdminMayApproveTheirOwnOrderButItIsAuditedAsSuch() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "1", "1"));
        authorities.add("FACILITIES_ADMIN");
        po.approve(tenant, r.id());
        assertTrue(audit.contains("PO_SELF_APPROVED"));
        assertFalse(audit.contains("PO_APPROVED"));
    }

    @Test
    void anIneligibleVendorBlocksApproval() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "1", "1"));
        vendorProblems = List.of("TAX_CLEARANCE expired");
        TenantContext.setUserId(approver.toString());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> po.approve(tenant, r.id()));
        assertEquals("VENDOR_NOT_ELIGIBLE", e.getErrorCode());
        assertTrue(e.getMessage().contains("TAX_CLEARANCE"));
        assertEquals("DRAFT", orders.get(r.id()).getStatus());
    }

    @Test
    void anApprovedOrderCannotBeEdited() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "1", "1"));
        TenantContext.setUserId(approver.toString());
        po.approve(tenant, r.id());
        assertEquals("PO_NOT_EDITABLE", assertThrows(HandyFlowException.class, () -> po.update(tenant, r.id(), orderOf(id, "2", "1"))).getErrorCode());
    }

    private PurchaseOrderResponse approvedOrder(UUID... itemIds) {
        List<PurchaseOrderRequest.Line> lines = new ArrayList<>();
        for (UUID i : itemIds) lines.add(new PurchaseOrderRequest.Line(i, n("10"), n("5.00")));
        TenantContext.setUserId(creator.toString());
        PurchaseOrderResponse r = po.create(tenant, new PurchaseOrderRequest(vendor.getId(), null, null, lines));
        TenantContext.setUserId(approver.toString());
        return po.approve(tenant, r.id());
    }

    @Test
    void aDraftOrderCannotBeReceived() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse r = po.create(tenant, orderOf(id, "1", "1"));
        UUID line = r.lines().get(0).id();
        assertEquals("PO_NOT_RECEIVABLE", assertThrows(HandyFlowException.class,
                () -> po.receive(tenant, r.id(), new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("1"), null))))).getErrorCode());
    }

    @Test
    void receivingBooksStockInAndMovesTheStatusOn() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();

        PurchaseOrderResponse part = po.receive(tenant, o.id(), new ReceiveOrderRequest("DN-77", List.of(new ReceiveOrderRequest.Line(line, n("4"), null))));
        assertEquals("PARTLY_RECEIVED", part.status());
        assertEquals(0, n("4").compareTo(items.get(id).getOnHand()));
        assertEquals(0, n("5.00").compareTo(items.get(id).getUnitCost()));
        FacilityStockMovement m = movements.get(movements.size() - 1);
        assertEquals(o.id(), m.getPurchaseOrderId());
        assertTrue(m.getReference().contains("DN-77"));

        PurchaseOrderResponse done = po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("6"), n("6.00")))));
        assertEquals("RECEIVED", done.status());
        assertEquals(0, n("10").compareTo(items.get(id).getOnHand()));
        // average of 4 @ 5.00 and 6 @ 6.00 = 5.60
        assertEquals(0, n("5.60").compareTo(items.get(id).getUnitCost()));
        assertTrue(audit.contains("PO_RECEIVED"));
    }

    @Test
    void overReceivingIsRefusedAndNothingIsBooked() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> po.receive(tenant, o.id(),
                new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("11"), null)))));
        assertEquals("OVER_RECEIPT", e.getErrorCode());
        assertEquals(0, items.get(id).getOnHand().signum());
    }

    @Test
    void aDeliveryWithOneBadLineBooksNone() {
        UUID a = newItem("A1", "0", "0", null, null);
        UUID b = newItem("B1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(a, b);
        UUID la = o.lines().get(0).id(), lb = o.lines().get(1).id();
        assertThrows(HandyFlowException.class, () -> po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(
                new ReceiveOrderRequest.Line(la, n("5"), null), new ReceiveOrderRequest.Line(lb, n("50"), null)))));
        assertEquals(0, items.get(a).getOnHand().signum());
        assertEquals("APPROVED", orders.get(o.id()).getStatus());
    }

    @Test
    void aLineCannotAppearTwiceInOneDeliveryOrComeFromAnotherOrder() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(
                new ReceiveOrderRequest.Line(line, n("1"), null), new ReceiveOrderRequest.Line(line, n("1"), null)))));
        assertThrows(IllegalArgumentException.class, () -> po.receive(tenant, o.id(), new ReceiveOrderRequest(null,
                List.of(new ReceiveOrderRequest.Line(UUID.randomUUID(), n("1"), null)))));
    }

    @Test
    void receivingCanCrossBackAboveTheReorderLevelWithoutAlerting() {
        UUID id = newItem("A1", "5", "10", "2", "1.00");
        alertTypes.clear();
        PurchaseOrderResponse o = approvedOrder(id);
        po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(o.lines().get(0).id(), n("10"), null))));
        assertTrue(alertTypes.isEmpty());
        assertFalse(items.get(id).isBelowReorderLevel());
    }

    @Test
    void cancelWorksUntilGoodsArriveThenYouCloseShort() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();
        po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("3"), null))));
        assertEquals("PO_NOT_CANCELLABLE", assertThrows(HandyFlowException.class, () -> po.cancel(tenant, o.id(), "no")).getErrorCode());
        PurchaseOrderResponse closed = po.closeShort(tenant, o.id(), "Vendor out of stock");
        assertEquals("RECEIVED", closed.status());
        assertEquals("Vendor out of stock", closed.closedReason());
        assertTrue(audit.contains("PO_CLOSED_SHORT"));

        PurchaseOrderResponse fresh = approvedOrder(id);
        assertEquals("CANCELLED", po.cancel(tenant, fresh.id(), "Duplicate").status());
        assertEquals("PO_NOT_CLOSABLE", assertThrows(HandyFlowException.class, () -> po.closeShort(tenant, fresh.id(), "x")).getErrorCode());
    }

    // ── goods receipts ──────────────────────────────────────────────────────────

    @Test
    void everyDeliveryProducesANumberedGoodsReceiptAtTheBookedCost() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();
        po.receive(tenant, o.id(), new ReceiveOrderRequest("DN-77", List.of(new ReceiveOrderRequest.Line(line, n("4"), n("5.50")))));
        po.receive(tenant, o.id(), new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("6"), null))));
        List<GoodsReceiptResponse> grns = po.goodsReceipts(tenant, o.id());
        assertEquals(List.of("FGR-1", "FGR-2"), grns.stream().map(GoodsReceiptResponse::grnNumber).toList());
        assertEquals("DN-77", grns.get(0).deliveryNote());
        assertEquals(0, n("22.00").compareTo(grns.get(0).totalValue()));   // 4 x 5.50
        assertEquals(0, n("30.00").compareTo(grns.get(1).totalValue()));   // 6 x ordered 5.00
        assertEquals(line, grns.get(0).lines().get(0).poLineId());
        assertTrue(audit.stream().anyMatch(a -> a.equals("PO_RECEIVED")));
    }

    @Test
    void aRefusedDeliveryLeavesNoGoodsReceipt() {
        UUID id = newItem("A1", "0", "0", null, null);
        PurchaseOrderResponse o = approvedOrder(id);
        UUID line = o.lines().get(0).id();
        assertThrows(HandyFlowException.class, () -> po.receive(tenant, o.id(),
                new ReceiveOrderRequest(null, List.of(new ReceiveOrderRequest.Line(line, n("11"), null)))));
        assertTrue(receipts.isEmpty());
    }

    // ── approval limits ─────────────────────────────────────────────────────────

    private PurchaseOrderResponse draftOf(String qty) {
        UUID id = newItem("L" + (++seq), "0", "0", null, null);
        TenantContext.setUserId(creator.toString());
        return po.create(tenant, orderOf(id, qty, "10.00"));
    }

    @Test
    void withNoLimitsConfiguredAnyApproverMayApprove() {
        PurchaseOrderResponse d = draftOf("1000");
        TenantContext.setUserId(approver.toString());
        assertEquals("APPROVED", po.approve(tenant, d.id()).status());
    }

    @Test
    void anApproverCannotApproveAboveTheirLimit() {
        limits.put(approver, FacilityApprovalLimit.create(tenant, approver, n("500.00"), creator));
        PurchaseOrderResponse d = draftOf("60");   // R600
        TenantContext.setUserId(approver.toString());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> po.approve(tenant, d.id()));
        assertEquals("PO_ABOVE_LIMIT", e.getErrorCode());
        assertTrue(e.getMessage().contains("R600.00") && e.getMessage().contains("R500.00"));
        assertEquals("DRAFT", orders.get(d.id()).getStatus());
    }

    @Test
    void anOrderAtTheLimitIsApprovable() {
        limits.put(approver, FacilityApprovalLimit.create(tenant, approver, n("500.00"), creator));
        PurchaseOrderResponse d = draftOf("50");   // R500 exactly
        TenantContext.setUserId(approver.toString());
        assertEquals("APPROVED", po.approve(tenant, d.id()).status());
    }

    @Test
    void onceLimitsExistAnApproverWithoutOneCannotApprove() {
        limits.put(UUID.randomUUID(), FacilityApprovalLimit.create(tenant, UUID.randomUUID(), n("100.00"), creator));
        PurchaseOrderResponse d = draftOf("1");
        TenantContext.setUserId(approver.toString());
        assertEquals("PO_ABOVE_LIMIT", assertThrows(HandyFlowException.class, () -> po.approve(tenant, d.id())).getErrorCode());
    }

    @Test
    void anAdminIsNeverLimited() {
        limits.put(UUID.randomUUID(), FacilityApprovalLimit.create(tenant, UUID.randomUUID(), n("1.00"), creator));
        PurchaseOrderResponse d = draftOf("1000");
        authorities.add("FACILITIES_ADMIN");
        TenantContext.setUserId(approver.toString());
        assertEquals("APPROVED", po.approve(tenant, d.id()).status());
    }

    // ── multiple stores ─────────────────────────────────────────────────────────────────────

    private FacilityStore newStore(String code) {
        FacilityStore st = FacilityStore.create(tenant, code, "Store " + code, null, false);
        stores.put(st.getId(), st);
        return st;
    }

    private BigDecimal levelOf(FacilityStore st, UUID item) {
        return levelList.stream().filter(l -> l.getStoreId().equals(st.getId()) && l.getItemId().equals(item)).map(FacilityStockLevel::getOnHand)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    @Test
    void theDefaultStoreIsCreatedOnFirstUseAndHoldsTheOpeningStock() {
        UUID id = newItem("S-1", "1", "0", "10", "5.00");
        assertEquals(1, stores.size());
        FacilityStore main = stores.values().iterator().next();
        assertTrue(main.isDefaultStore());
        assertEquals("MAIN", main.getCode());
        assertEquals(0, n("10").compareTo(levelOf(main, id)));
    }

    @Test
    void receivingIntoANamedStoreLeavesTheDefaultAlone() {
        UUID id = newItem("S-2", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN1");
        stock.receive(tenant, id, new ReceiveStockRequest(n("4"), n("5.00"), null), van.getId());
        assertEquals(0, n("4").compareTo(levelOf(van, id)));
        assertEquals(0, n("14").compareTo(stock.get(tenant, id).onHand()));
        assertEquals(van.getId(), movements.get(movements.size() - 1).getStoreId());
    }

    @Test
    void issuingIsLimitedToTheChosenStoresLevel() {
        UUID id = newItem("S-3", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN2");
        stock.receive(tenant, id, new ReceiveStockRequest(n("2"), n("5.00"), null), van.getId());
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> stock.issue(tenant, id, new IssueStockRequest(UUID.randomUUID(), n("3"), null), van.getId()));
        assertEquals("INSUFFICIENT_STOCK", e.getErrorCode());
        assertTrue(e.getMessage().contains("Store VAN2"));
        // The item total (12) would have covered it; the store (2) does not.
        assertEquals(0, n("12").compareTo(stock.get(tenant, id).onHand()));
    }

    @Test
    void aTransferMovesStockButNotTheTotalAndWritesTwoMovements() {
        UUID id = newItem("S-4", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN3");
        FacilityStore main = stores.values().stream().filter(FacilityStore::isDefaultStore).findFirst().orElseThrow();
        int before = movements.size();
        stock.transfer(tenant, id, new TransferStockRequest(main.getId(), van.getId(), n("4"), "for call-outs"));
        assertEquals(0, n("6").compareTo(levelOf(main, id)));
        assertEquals(0, n("4").compareTo(levelOf(van, id)));
        assertEquals(0, n("10").compareTo(stock.get(tenant, id).onHand()));
        assertEquals(2, movements.size() - before);
        assertEquals("TRANSFER_OUT", movements.get(before).getMovementType());
        assertEquals("TRANSFER_IN", movements.get(before + 1).getMovementType());
        assertTrue(audit.contains("STOCK_TRANSFERRED"));
    }

    @Test
    void aTransferRefusesTheSameStoreAndMoreThanTheSourceHolds() {
        UUID id = newItem("S-5", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN4");
        FacilityStore main = stores.values().stream().filter(FacilityStore::isDefaultStore).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> stock.transfer(tenant, id, new TransferStockRequest(main.getId(), main.getId(), n("1"), null)));
        assertEquals("INSUFFICIENT_STOCK", assertThrows(HandyFlowException.class,
                () -> stock.transfer(tenant, id, new TransferStockRequest(van.getId(), main.getId(), n("1"), null))).getErrorCode());
    }

    @Test
    void aStocktakeCountsOneStoreAndMovesTheTotalByTheDifference() {
        UUID id = newItem("S-6", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN5");
        stock.receive(tenant, id, new ReceiveStockRequest(n("5"), n("5.00"), null), van.getId());
        stock.adjust(tenant, id, new AdjustStockRequest(n("3"), "two missing"), van.getId());
        assertEquals(0, n("3").compareTo(levelOf(van, id)));
        assertEquals(0, n("13").compareTo(stock.get(tenant, id).onHand()));
    }

    @Test
    void anInactiveOrUnknownStoreIsRefused() {
        UUID id = newItem("S-7", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN6");
        van.deactivate();
        assertEquals("STORE_INACTIVE", assertThrows(HandyFlowException.class,
                () -> stock.receive(tenant, id, new ReceiveStockRequest(n("1"), n("5.00"), null), van.getId())).getErrorCode());
        assertThrows(ResourceNotFoundException.class,
                () -> stock.receive(tenant, id, new ReceiveStockRequest(n("1"), n("5.00"), null), UUID.randomUUID()));
    }

    @Test
    void levelsListsEachStoreThatHoldsTheItem() {
        UUID id = newItem("S-8", "1", "0", "10", "5.00");
        FacilityStore van = newStore("VAN7");
        stock.receive(tenant, id, new ReceiveStockRequest(n("2"), n("5.00"), null), van.getId());
        assertEquals(2, stock.levels(tenant, id).size());
    }
}
