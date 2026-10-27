package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.application.internal.FacilityReorderRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Stock arithmetic, reorder rules and the purchase order state machine. */
class FacilityStockAndPurchaseOrderModelTest {

    private final TenantId tenant = TenantId.generate();

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private FacilityStockItem item(String sku, String level, String qty) {
        return FacilityStockItem.create(tenant, sku, "Item " + sku, "each", null, n(level), n(qty));
    }

    private FacilityPurchaseOrder order(FacilityStockItem... items) {
        List<FacilityPurchaseOrder.LineSpec> specs = java.util.Arrays.stream(items)
                .map(i -> new FacilityPurchaseOrder.LineSpec(i.getId(), i.getSku(), n("10"), n("5.00"))).toList();
        return FacilityPurchaseOrder.create(tenant, "FPO-1", UUID.randomUUID(), null, null, UUID.randomUUID(), specs);
    }

    // ── stock item ──────────────────────────────────────────────────────────────

    @Test
    void skuIsTrimmedAndUpperCased() {
        assertEquals("PUMP-01", item("  pump-01 ", "0", "0").getSku());
    }

    @Test
    void receivingMovesTheAverageCostToTheWeightedAverage() {
        FacilityStockItem i = item("A", "0", "0");
        i.receive(n("10"), n("10.00"));
        i.receive(n("10"), n("20.00"));
        assertEquals(0, n("20").compareTo(i.getOnHand()));
        assertEquals(0, n("15.00").compareTo(i.getUnitCost()));
        assertEquals(0, n("300.00").compareTo(i.stockValue()));
    }

    @Test
    void receivingIntoEmptyStockTakesTheReceiptCost() {
        FacilityStockItem i = item("A", "0", "0");
        i.receive(n("4"), n("12.50"));
        i.issue(n("4"));
        i.receive(n("2"), n("30.00"));
        assertEquals(0, n("30.00").compareTo(i.getUnitCost()));
    }

    @Test
    void issuingNeverGoesNegativeAndLeavesTheCostAlone() {
        FacilityStockItem i = item("A", "0", "0");
        i.receive(n("5"), n("8.00"));
        assertThrows(IllegalArgumentException.class, () -> i.issue(n("5.001")));
        assertEquals(0, n("5").compareTo(i.getOnHand()));
        i.issue(n("2"));
        assertEquals(0, n("3").compareTo(i.getOnHand()));
        assertEquals(0, n("8.00").compareTo(i.getUnitCost()));
    }

    @Test
    void quantitiesMustBePositive() {
        FacilityStockItem i = item("A", "0", "0");
        assertThrows(IllegalArgumentException.class, () -> i.receive(BigDecimal.ZERO, n("1")));
        assertThrows(IllegalArgumentException.class, () -> i.receive(n("1"), n("-1")));
        assertThrows(IllegalArgumentException.class, () -> i.issue(n("-1")));
        assertThrows(IllegalArgumentException.class, () -> i.putBack(BigDecimal.ZERO));
    }

    @Test
    void aStocktakeReturnsTheDifference() {
        FacilityStockItem i = item("A", "0", "0");
        i.receive(n("10"), n("1.00"));
        assertEquals(0, n("-3").compareTo(i.count(n("7"))));
        assertEquals(0, n("5").compareTo(i.count(n("12"))));
        assertThrows(IllegalArgumentException.class, () -> i.count(n("-1")));
    }

    @Test
    void belowReorderLevelNeedsALevelAndIsInclusive() {
        FacilityStockItem none = item("N", "0", "0");
        assertFalse(none.isBelowReorderLevel());
        FacilityStockItem i = item("A", "5", "20");
        i.receive(n("6"), n("1"));
        assertFalse(i.isBelowReorderLevel());
        i.issue(n("1"));
        assertTrue(i.isBelowReorderLevel());
    }

    // ── reorder rules ───────────────────────────────────────────────────────────

    @Test
    void suggestsTheStandardQuantityOrWhatRestoresTwiceTheLevel() {
        FacilityStockItem small = item("A", "10", "5");   // on hand 2 -> target 18, standard 5 -> 18
        small.receive(n("2"), n("1"));
        FacilityStockItem big = item("B", "10", "50");    // standard 50 wins
        big.receive(n("2"), n("1"));
        var out = FacilityReorderRules.suggest(List.of(small, big), Map.of());
        assertEquals(2, out.size());
        assertEquals(0, n("18").compareTo(out.get(0).suggestedQuantity()));
        assertEquals(0, n("50").compareTo(out.get(1).suggestedQuantity()));
    }

    @Test
    void openOrdersThatCoverTheGapSuppressTheSuggestion() {
        FacilityStockItem i = item("A", "10", "20");
        i.receive(n("4"), n("1"));
        assertTrue(FacilityReorderRules.suggest(List.of(i), Map.of(i.getId(), n("20"))).isEmpty());
        var partial = FacilityReorderRules.suggest(List.of(i), Map.of(i.getId(), n("5")));
        assertEquals(1, partial.size());
        assertEquals(0, n("5").compareTo(partial.get(0).onOrder()));
    }

    // ── purchase order ──────────────────────────────────────────────────────────

    @Test
    void anOrderNeedsLinesAndNoDuplicateItems() {
        FacilityStockItem a = item("A", "0", "0");
        assertThrows(IllegalArgumentException.class, () -> FacilityPurchaseOrder.create(tenant, "X", UUID.randomUUID(), null, null, null, List.of()));
        var dup = new FacilityPurchaseOrder.LineSpec(a.getId(), "A", n("1"), n("1"));
        assertThrows(IllegalArgumentException.class, () -> FacilityPurchaseOrder.create(tenant, "X", UUID.randomUUID(), null, null, null, List.of(dup, dup)));
    }

    @Test
    void totalIsTheSumOfLines() {
        FacilityPurchaseOrder po = order(item("A", "0", "0"), item("B", "0", "0"));
        assertEquals(0, n("100.00").compareTo(po.totalValue()));
    }

    @Test
    void onlyADraftCanBeEditedOrApproved() {
        FacilityStockItem a = item("A", "0", "0");
        FacilityPurchaseOrder po = order(a);
        po.approve(UUID.randomUUID());
        assertEquals("APPROVED", po.getStatus());
        assertThrows(IllegalStateException.class, () -> po.approve(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> po.revise(UUID.randomUUID(), null, null,
                List.of(new FacilityPurchaseOrder.LineSpec(a.getId(), "A", n("1"), n("1")))));
    }

    @Test
    void goodsCannotBeReceivedUntilApproved() {
        FacilityPurchaseOrder po = order(item("A", "0", "0"));
        UUID line = po.getLines().get(0).getId();
        assertThrows(IllegalStateException.class, () -> po.receiveLine(line, n("1")));
    }

    @Test
    void partialThenFullDeliveryMovesTheStatusOn() {
        FacilityPurchaseOrder po = order(item("A", "0", "0"), item("B", "0", "0"));
        po.approve(UUID.randomUUID());
        UUID a = po.getLines().get(0).getId();
        UUID b = po.getLines().get(1).getId();
        po.receiveLine(a, n("10"));
        assertEquals("PARTLY_RECEIVED", po.getStatus());
        po.receiveLine(b, n("4"));
        assertEquals("PARTLY_RECEIVED", po.getStatus());
        po.receiveLine(b, n("6"));
        assertEquals("RECEIVED", po.getStatus());
    }

    @Test
    void overReceivingIsRefusedAndLeavesTheLineUntouched() {
        FacilityPurchaseOrder po = order(item("A", "0", "0"));
        po.approve(UUID.randomUUID());
        UUID line = po.getLines().get(0).getId();
        po.receiveLine(line, n("8"));
        assertThrows(IllegalStateException.class, () -> po.receiveLine(line, n("3")));
        assertEquals(0, n("8").compareTo(po.getLines().get(0).getReceivedQty()));
    }

    @Test
    void cancelNeedsAReasonAndNothingReceived() {
        FacilityPurchaseOrder draft = order(item("A", "0", "0"));
        assertThrows(IllegalArgumentException.class, () -> draft.cancel(" "));
        draft.cancel("Vendor withdrew");
        assertEquals("CANCELLED", draft.getStatus());

        FacilityPurchaseOrder part = order(item("B", "0", "0"));
        part.approve(UUID.randomUUID());
        part.receiveLine(part.getLines().get(0).getId(), n("1"));
        assertThrows(IllegalStateException.class, () -> part.cancel("changed mind"));
    }

    @Test
    void aPartDeliveredOrderCanBeClosedShortButNotBefore() {
        FacilityPurchaseOrder po = order(item("A", "0", "0"));
        po.approve(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> po.closeShort("n/a"));
        po.receiveLine(po.getLines().get(0).getId(), n("3"));
        po.closeShort("Vendor discontinued the rest");
        assertEquals("RECEIVED", po.getStatus());
        assertFalse(po.isOpen());
    }
}
