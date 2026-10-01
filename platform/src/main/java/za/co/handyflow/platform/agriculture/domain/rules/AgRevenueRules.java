package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Revenue rules for allocating sales to production (ADR-001, W2). Agriculture does not own sales: an invoice line stays in Invoicing and
 * Agriculture only records which part of it belongs to which crop cycle, group, animal or enterprise. Revenue is therefore computed LIVE
 * from the invoice each time, so a cancelled invoice or a later credit note changes it, instead of being copied and going stale.
 * <p>
 * Rules (ADR-001 decisions 2 and 3): revenue is ex-VAT; it counts once an invoice is issued (not draft, not cancelled); and credit notes
 * are netted in proportion across the invoice's lines, because a credit note belongs to the invoice, not to a line.
 * <p>
 * Pure and dependency-free so the arithmetic is unit tested without Spring or a database.
 */
public final class AgRevenueRules {

    private AgRevenueRules() {}

    /** Invoice statuses that count as revenue: issued onwards. DRAFT and CANCELLED never do. */
    public static final Set<String> RECOGNISED_STATUSES = Set.of("ISSUED", "PARTIALLY_PAID", "PAID", "OVERPAID", "OVERDUE");

    /** Quantities are held to three decimals; this absorbs rounding when a line is allocated in full. */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.0005");

    public static boolean isRecognised(String invoiceStatus) {
        return invoiceStatus != null && RECOGNISED_STATUSES.contains(invoiceStatus);
    }

    /** The share of an invoice that has been credited, between 0 and 1 (a credit larger than the invoice counts as fully credited). */
    public static BigDecimal creditRatio(BigDecimal creditedSubtotal, BigDecimal invoiceSubtotal) {
        if (creditedSubtotal == null || creditedSubtotal.signum() <= 0 || invoiceSubtotal == null || invoiceSubtotal.signum() <= 0) return BigDecimal.ZERO;
        if (creditedSubtotal.compareTo(invoiceSubtotal) >= 0) return BigDecimal.ONE;
        return creditedSubtotal.divide(invoiceSubtotal, 6, RoundingMode.HALF_UP);
    }

    /** A line's ex-VAT total after its proportional share of the invoice's credit notes, to the cent. */
    public static BigDecimal netLineRevenue(BigDecimal lineTotal, BigDecimal creditedSubtotal, BigDecimal invoiceSubtotal) {
        if (lineTotal == null) return BigDecimal.ZERO.setScale(2);
        BigDecimal kept = BigDecimal.ONE.subtract(creditRatio(creditedSubtotal, invoiceSubtotal));
        return lineTotal.multiply(kept).setScale(2, RoundingMode.HALF_UP);
    }

    /** How much of a line can still be allocated, never negative. */
    public static BigDecimal remainingQuantity(BigDecimal lineQuantity, BigDecimal alreadyAllocated) {
        BigDecimal left = nz(lineQuantity).subtract(nz(alreadyAllocated));
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }

    /** @throws IllegalArgumentException when the new quantity is not positive or would allocate more than the line holds */
    public static void requireWithinLine(BigDecimal lineQuantity, BigDecimal alreadyAllocated, BigDecimal newQuantity) {
        if (newQuantity == null || newQuantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        BigDecimal remaining = remainingQuantity(lineQuantity, alreadyAllocated);
        if (newQuantity.subtract(remaining).compareTo(QUANTITY_TOLERANCE) > 0) {
            throw new IllegalArgumentException("only " + remaining.stripTrailingZeros().toPlainString() + " is left to allocate on this invoice line");
        }
    }

    /**
     * Splits the revenue of the allocated part of a line across its allocations, in proportion to their quantities, and always adds up
     * EXACTLY to that part to the cent (largest-remainder apportionment). So a R100.00 line allocated 1:1:1 is R33.34 + R33.33 + R33.33, and
     * a line allocated only 40% yields exactly 40% of its revenue.
     *
     * @param netLineRevenue revenue of the whole line after credit notes
     * @param lineQuantity   quantity on the invoice line
     * @param quantities     the quantity of each allocation of this line, in order
     */
    public static List<BigDecimal> apportion(BigDecimal netLineRevenue, BigDecimal lineQuantity, List<BigDecimal> quantities) {
        int n = quantities.size();
        List<BigDecimal> out = new ArrayList<>();
        if (n == 0) return out;
        if (netLineRevenue == null || netLineRevenue.signum() <= 0 || lineQuantity == null || lineQuantity.signum() <= 0) {
            for (int i = 0; i < n; i++) out.add(BigDecimal.ZERO.setScale(2));
            return out;
        }
        long netCents = netLineRevenue.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        BigDecimal sumQty = BigDecimal.ZERO;
        for (BigDecimal q : quantities) sumQty = sumQty.add(nz(q));
        // Share against whichever is larger: the line, or what was allocated. A hair over the line (inside the quantity tolerance) must
        // never pay out more than the line earned.
        BigDecimal basis = lineQuantity.max(sumQty);
        long allocatedCents = BigDecimal.valueOf(netCents).multiply(sumQty).divide(basis, 0, RoundingMode.HALF_UP).longValueExact();

        long[] cents = new long[n];
        BigDecimal[] fraction = new BigDecimal[n];
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = BigDecimal.valueOf(netCents).multiply(nz(quantities.get(i))).divide(basis, 12, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.DOWN);
            cents[i] = floor.longValueExact();
            fraction[i] = exact.subtract(floor);
            assigned += cents[i];
        }
        long leftover = allocatedCents - assigned;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((a, b) -> fraction[b].compareTo(fraction[a]) != 0 ? fraction[b].compareTo(fraction[a]) : Integer.compare(a, b));
        for (int k = 0; leftover > 0; k = (k + 1) % n, leftover--) cents[order.get(k)]++;
        for (int i = 0; i < n; i++) out.add(BigDecimal.valueOf(cents[i], 2));
        return out;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
