package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpend;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the spend-by-supplier report from what the service loaded (ADR-001, W7). Pure: no repositories.
 * <p>
 * Every receipt is in exactly one row, so the rows always add up to the total: a receipt with no supplier goes under "No supplier recorded", and one whose supplier has
 * since been removed from Supply Chain under "Unknown supplier (removed)". Spend is each receipt's own recorded cost.
 */
public final class AgPurchasesAggregator {

    private AgPurchasesAggregator() {}

    public static final String NO_SUPPLIER = "No supplier recorded";
    public static final String UNKNOWN_SUPPLIER = "Unknown supplier (removed)";

    /** One group from the database: all receipts of one supplier (null = none recorded). */
    public record Row(UUID supplierId, long receipts, BigDecimal spend, long receiptsWithoutCost) {}

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    public static SupplierSpendResponse build(LocalDate from, LocalDate to, List<Row> rows, Map<UUID, String> supplierNames) {
        BigDecimal total = BigDecimal.ZERO;
        long receipts = 0, withoutCost = 0;
        for (Row r : rows) { total = total.add(nz(r.spend())); receipts += r.receipts(); withoutCost += r.receiptsWithoutCost(); }
        BigDecimal totalSpend = total.setScale(2, RoundingMode.HALF_UP);

        List<SupplierSpend> out = new ArrayList<>();
        boolean anyNone = false, anyUnknown = false;
        for (Row r : rows) {
            String name;
            if (r.supplierId() == null) { name = NO_SUPPLIER; anyNone = true; }
            else if (supplierNames.get(r.supplierId()) == null) { name = UNKNOWN_SUPPLIER; anyUnknown = true; }
            else name = supplierNames.get(r.supplierId());
            BigDecimal spend = nz(r.spend()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = total.signum() > 0 ? nz(r.spend()).multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP) : null;
            out.add(new SupplierSpend(r.supplierId(), name, (int) r.receipts(), spend, share));
        }
        out.sort(Comparator.comparing(SupplierSpend::spend).reversed().thenComparing(s -> s.supplierName().toLowerCase()));

        List<String> notes = new ArrayList<>();
        notes.add("Spend is the cost of stock RECEIVED into Agriculture inventory (quantity x unit cost when it was received). It does not include purchase orders or supplier invoices in Supply Chain, or anything bought but never received into stock.");
        if (withoutCost > 0) notes.add(withoutCost + " receipt" + (withoutCost == 1 ? " has" : "s have") + " no unit cost recorded, so " + (withoutCost == 1 ? "it counts" : "they count") + " as no spend.");
        if (anyNone) notes.add("Receipts with no supplier recorded are listed together. Choose a supplier when receiving stock, or set an item's usual supplier, to see where the money goes.");
        if (anyUnknown) notes.add("Some receipts were bought from a supplier that has since been removed from Supply Chain.");

        return new SupplierSpendResponse(from, to, List.copyOf(out), totalSpend, (int) receipts, (int) withoutCost, List.copyOf(notes));
    }
}
