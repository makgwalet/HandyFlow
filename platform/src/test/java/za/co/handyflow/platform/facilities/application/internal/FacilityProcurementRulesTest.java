package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.application.internal.FacilityProcurementRules.Match;
import za.co.handyflow.platform.facilities.domain.model.FacilityGoodsReceipt;
import za.co.handyflow.platform.facilities.domain.model.FacilityGoodsReceiptLine;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrder;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrderLine;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityProcurementRulesTest {

    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private final TenantId tenant = TenantId.generate();

    // ── match ────────────────────────────────────────────────────────────────

    @Test
    void nothingInvoicedYetIsNotInvoiced() {
        assertEquals("NOT_INVOICED", FacilityProcurementRules.evaluate(n("1000"), n("1000"), BigDecimal.ZERO).status());
    }

    @Test
    void billedWithNothingDeliveredIsFlagged() {
        Match m = FacilityProcurementRules.evaluate(n("1000"), BigDecimal.ZERO, n("500"));
        assertEquals("NOT_RECEIVED", m.status());
        assertTrue(m.needsExplanation());
    }

    @Test
    void withinTheToleranceItMatches() {
        // 1% of 2000 = 20.00 (above the R5 floor)
        assertEquals("MATCHED", FacilityProcurementRules.evaluate(n("2000"), n("2000"), n("2020.00")).status());
        assertEquals("MATCHED", FacilityProcurementRules.evaluate(n("2000"), n("2000"), n("1980.00")).status());
        assertEquals("OVER_INVOICED", FacilityProcurementRules.evaluate(n("2000"), n("2000"), n("2020.01")).status());
        assertEquals("UNDER_INVOICED", FacilityProcurementRules.evaluate(n("2000"), n("2000"), n("1979.99")).status());
    }

    @Test
    void smallOrdersUseTheFiveRandFloor() {
        Match m = FacilityProcurementRules.evaluate(n("100"), n("100"), n("105.00"));
        assertEquals(0, n("5.00").compareTo(m.tolerance()));
        assertEquals("MATCHED", m.status());
        assertEquals("OVER_INVOICED", FacilityProcurementRules.evaluate(n("100"), n("100"), n("105.01")).status());
    }

    @Test
    void onlyOverBillingAndBillingWithoutDeliveryNeedAnExplanation() {
        assertTrue(FacilityProcurementRules.evaluate(n("100"), n("100"), n("200")).needsExplanation());
        assertFalse(FacilityProcurementRules.evaluate(n("100"), n("100"), n("40")).needsExplanation());
        assertFalse(FacilityProcurementRules.evaluate(n("100"), n("100"), n("100")).needsExplanation());
    }

    @Test
    void varianceIsInvoicedMinusReceived() {
        assertEquals(0, n("-60.00").compareTo(FacilityProcurementRules.evaluate(n("100"), n("100"), n("40")).variance()));
    }

    // ── received value ───────────────────────────────────────────────────────

    private FacilityPurchaseOrder order(String qty, String cost) {
        return FacilityPurchaseOrder.create(tenant, "FPO-1", UUID.randomUUID(), null, null, UUID.randomUUID(),
                List.of(new FacilityPurchaseOrder.LineSpec(UUID.randomUUID(), "Item", n(qty), n(cost))));
    }

    private FacilityGoodsReceiptLine grnLine(FacilityPurchaseOrderLine l, String qty, String cost) {
        FacilityGoodsReceipt r = FacilityGoodsReceipt.create(tenant, "FGR-1", UUID.randomUUID(), UUID.randomUUID(), null, LocalDate.now(),
                null, List.of(new FacilityGoodsReceipt.LineSpec(l.getId(), l.getItemId(), l.getDescription(), n(qty), n(cost))));
        return r.getLines().get(0);
    }

    @Test
    void receivedValueUsesTheCostBookedOnTheReceipt() {
        FacilityPurchaseOrder po = order("10", "5.00");
        po.approve(UUID.randomUUID());
        FacilityPurchaseOrderLine line = po.getLines().get(0);
        po.receiveLine(line.getId(), n("4"));
        assertEquals(0, n("22.00").compareTo(FacilityProcurementRules.receivedValue(po.getLines(), List.of(grnLine(line, "4", "5.50")))));
    }

    @Test
    void quantityReceivedBeforeReceiptsExistedIsValuedAtTheOrderPrice() {
        FacilityPurchaseOrder po = order("10", "5.00");
        po.approve(UUID.randomUUID());
        FacilityPurchaseOrderLine line = po.getLines().get(0);
        po.receiveLine(line.getId(), n("4"));   // legacy: no receipt document
        po.receiveLine(line.getId(), n("3"));   // documented
        assertEquals(0, n("36.50").compareTo(FacilityProcurementRules.receivedValue(po.getLines(), List.of(grnLine(line, "3", "5.50")))));
        // 4 legacy x 5.00 = 20.00 + 3 x 5.50 = 16.50 -> 36.50
    }

    // ── approval limits ──────────────────────────────────────────────────────

    @Test
    void approvalLimitRules() {
        assertNull(FacilityProcurementRules.approvalProblem(false, false, null, n("999999")));       // nothing configured
        assertNull(FacilityProcurementRules.approvalProblem(true, true, null, n("999999")));         // admin
        assertNull(FacilityProcurementRules.approvalProblem(false, true, n("500"), n("500")));       // at limit
        assertNotNull(FacilityProcurementRules.approvalProblem(false, true, n("500"), n("500.01"))); // over
        assertNotNull(FacilityProcurementRules.approvalProblem(false, true, null, n("1")));          // no limit of their own
    }
}
