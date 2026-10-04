package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.application.internal.AgPurchasesAggregator.Row;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpend;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgPurchasesAggregatorTest {

    static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 9, 30);
    final UUID agri = UUID.randomUUID(), feed = UUID.randomUUID(), gone = UUID.randomUUID();
    final Map<UUID, String> names = Map.of(agri, "AgriSupplies", feed, "Feed Co");

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void num(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }
    private static Row row(UUID supplier, long receipts, String spend, long withoutCost) { return new Row(supplier, receipts, bd(spend), withoutCost); }
    private SupplierSpendResponse build(Row... rows) { return AgPurchasesAggregator.build(FROM, TO, List.of(rows), names); }

    @Test
    @DisplayName("each supplier shows its name, receipt count and spend; the total is their sum")
    void basics() {
        SupplierSpendResponse r = build(row(agri, 3, "1500.50", 0), row(feed, 2, "500.25", 0));

        assertEquals(List.of("AgriSupplies", "Feed Co"), r.suppliers().stream().map(SupplierSpend::supplierName).toList());
        assertEquals(3, r.suppliers().get(0).receipts()); num("1500.50", r.suppliers().get(0).spend());
        num("2000.75", r.totalSpend()); assertEquals(5, r.receipts());
        assertEquals(FROM, r.from()); assertEquals(TO, r.to());
    }

    @Test
    @DisplayName("the rows always add up to the total, including receipts with no supplier and from a removed one")
    void rowsAddUpToTheTotal() {
        SupplierSpendResponse r = build(row(agri, 1, "100.10", 0), row(null, 2, "200.20", 0), row(gone, 1, "300.30", 0));

        BigDecimal sum = r.suppliers().stream().map(SupplierSpend::spend).reduce(BigDecimal.ZERO, BigDecimal::add);
        num(sum.toPlainString(), r.totalSpend());
        num("600.60", r.totalSpend());
        assertEquals(4, r.suppliers().stream().mapToInt(SupplierSpend::receipts).sum());
    }

    @Test
    @DisplayName("a receipt with no supplier is under 'No supplier recorded'; one whose supplier was removed is under 'Unknown supplier (removed)'")
    void noneAndUnknown() {
        SupplierSpendResponse r = build(row(null, 2, "200", 0), row(gone, 1, "300", 0));

        SupplierSpend none = r.suppliers().stream().filter(s -> s.supplierId() == null).findFirst().orElseThrow();
        SupplierSpend unknown = r.suppliers().stream().filter(s -> gone.equals(s.supplierId())).findFirst().orElseThrow();
        assertEquals("No supplier recorded", none.supplierName());
        assertEquals("Unknown supplier (removed)", unknown.supplierName());
    }

    @Test
    @DisplayName("share is of the total to one decimal, half up, and null when the total is zero")
    void shares() {
        SupplierSpendResponse r = build(row(agri, 1, "200", 0), row(feed, 1, "100", 0));
        num("66.7", r.suppliers().get(0).sharePercent()); num("33.3", r.suppliers().get(1).sharePercent());

        SupplierSpendResponse zero = build(row(agri, 2, "0", 2));
        assertNull(zero.suppliers().get(0).sharePercent());
    }

    @Test
    @DisplayName("suppliers are ordered by spend, biggest first, then by name ignoring case")
    void ordering() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        // the three R100 suppliers tie, so they are ordered by name: case-insensitively that is alpha, Bravo, zebra (a case-sensitive sort would put Bravo first)
        SupplierSpendResponse r = AgPurchasesAggregator.build(FROM, TO, List.of(row(a, 1, "100", 0), row(b, 1, "100", 0), row(c, 1, "500", 0), row(d, 1, "100", 0)), Map.of(a, "zebra", b, "alpha", c, "Middle", d, "Bravo"));

        assertEquals(List.of("Middle", "alpha", "Bravo", "zebra"), r.suppliers().stream().map(SupplierSpend::supplierName).toList());
    }

    @Test
    @DisplayName("receipts with no unit cost are counted, count as no spend, and are said so, in the singular and the plural")
    void withoutCost() {
        SupplierSpendResponse one = build(row(agri, 3, "100", 1));
        assertEquals(1, one.receiptsWithoutCost()); assertEquals(3, one.receipts());
        assertTrue(one.notes().stream().anyMatch(n -> n.contains("1 receipt has no unit cost recorded, so it counts as no spend")), one.notes().toString());

        SupplierSpendResponse many = build(row(agri, 5, "100", 2), row(feed, 1, "10", 1));
        assertEquals(3, many.receiptsWithoutCost());
        assertTrue(many.notes().stream().anyMatch(n -> n.contains("3 receipts have no unit cost recorded, so they count as no spend")), many.notes().toString());

        assertTrue(build(row(agri, 1, "100", 0)).notes().stream().noneMatch(n -> n.contains("no unit cost")));
    }

    @Test
    @DisplayName("the standard note says what spend is and is not: received stock, not Supply Chain purchase orders or invoices")
    void standardNote() {
        List<String> notes = build(row(agri, 1, "100", 0)).notes();
        assertTrue(notes.get(0).contains("RECEIVED into Agriculture inventory") && notes.get(0).contains("purchase orders or supplier invoices"), notes.get(0));
    }

    @Test
    @DisplayName("the no-supplier and removed-supplier notes appear only when those rows do")
    void conditionalNotes() {
        assertTrue(build(row(agri, 1, "100", 0)).notes().stream().noneMatch(n -> n.contains("no supplier recorded") || n.contains("since been removed")));
        assertTrue(build(row(null, 1, "100", 0)).notes().stream().anyMatch(n -> n.contains("no supplier recorded are listed together")));
        assertTrue(build(row(gone, 1, "100", 0)).notes().stream().anyMatch(n -> n.contains("since been removed from Supply Chain")));
    }

    @Test
    @DisplayName("spend is shown to two places, and a null spend is zero")
    void rounding() {
        SupplierSpendResponse r = build(row(agri, 1, "10.005", 0), new Row(feed, 1, null, 1));
        num("10.01", r.suppliers().get(0).spend()); assertEquals(2, r.suppliers().get(0).spend().scale());
        num("0.00", r.suppliers().get(1).spend());
        assertEquals(2, build(row(agri, 1, "100", 0), row(feed, 1, "200", 0)).totalSpend().scale());
    }

    @Test
    @DisplayName("nothing received gives an empty report with a zero total and just the standard note")
    void empty() {
        SupplierSpendResponse r = AgPurchasesAggregator.build(FROM, TO, List.of(), names);

        assertTrue(r.suppliers().isEmpty()); num("0.00", r.totalSpend()); assertEquals(0, r.receipts());
        assertEquals(1, r.notes().size());
    }
}
