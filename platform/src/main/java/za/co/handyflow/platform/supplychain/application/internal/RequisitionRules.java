package za.co.handyflow.platform.supplychain.application.internal;

import za.co.handyflow.platform.supplychain.domain.enums.RequisitionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure requisition rules — validation, totals, stock advisories, fulfilment. No repositories. */
public final class RequisitionRules {

    private RequisitionRules() {}

    public record LineInput(String itemName, BigDecimal qty, BigDecimal estUnitCost, BigDecimal vatRate) {}
    public record Availability(BigDecimal onHand, BigDecimal onOrder, BigDecimal alreadyRequested) {
        public static final Availability NONE = new Availability(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public enum Fulfilment { NOT_ORDERED, PARTIALLY_ORDERED, ORDERED }

    /** Validation applied on every save; returns human-readable errors (empty = valid). */
    public static List<String> validate(RequisitionType type, String title, String reason, String projectRef,
                                        List<LineInput> lines) {
        List<String> e = new ArrayList<>();
        if (type == null) e.add("Requisition type is required");
        if (title == null || title.isBlank()) e.add("A title is required");
        if (reason == null || reason.isBlank()) e.add("A business reason is required");
        if (type == RequisitionType.PROJECT && (projectRef == null || projectRef.isBlank()))
            e.add("A PROJECT requisition needs a project reference");
        int n = 1;
        for (LineInput l : lines) {
            if (l.itemName() == null || l.itemName().isBlank()) e.add("Line " + n + ": item name is required");
            if (l.qty() == null || l.qty().signum() <= 0) e.add("Line " + n + ": quantity must be greater than zero");
            if (l.estUnitCost() == null || l.estUnitCost().signum() < 0) e.add("Line " + n + ": estimated unit cost is required");
            n++;
        }
        return e;
    }

    /** Estimated total including VAT, same 2dp HALF_UP rounding as PO lines. */
    public static BigDecimal estimatedTotal(List<LineInput> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineInput l : lines) {
            BigDecimal net = l.qty().multiply(l.estUnitCost()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = net.multiply(l.vatRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            total = total.add(net).add(vat);
        }
        return total;
    }

    /** Advisory messages for the approver/requester — never blocking. */
    public static List<String> advisories(String item, BigDecimal requested, Availability a) {
        List<String> out = new ArrayList<>();
        BigDecimal covered = a.onHand().add(a.onOrder());
        if (covered.compareTo(requested) >= 0) {
            out.add(item + ": " + plain(a.onHand()) + " on hand and " + plain(a.onOrder())
                    + " already on order cover the " + plain(requested) + " requested");
        } else if (a.onOrder().signum() > 0) {
            out.add(item + ": " + plain(a.onOrder()) + " already on open purchase orders");
        }
        if (a.alreadyRequested().signum() > 0) {
            out.add(item + ": " + plain(a.alreadyRequested()) + " already requested on other open requisitions");
        }
        return out;
    }

    /** Derived from non-cancelled conversions: ordered quantity per line vs requested. */
    public static Fulfilment fulfilment(List<BigDecimal[]> requestedAndOrderedPerLine) {
        boolean anyOrdered = false, allOrdered = !requestedAndOrderedPerLine.isEmpty();
        for (BigDecimal[] l : requestedAndOrderedPerLine) {
            if (l[1].signum() > 0) anyOrdered = true;
            if (l[1].compareTo(l[0]) < 0) allOrdered = false;
        }
        return allOrdered ? Fulfilment.ORDERED : anyOrdered ? Fulfilment.PARTIALLY_ORDERED : Fulfilment.NOT_ORDERED;
    }

    /** Suggested replenishment quantity: the configured reorder qty, else enough to reach twice the reorder point. */
    public static BigDecimal suggestedQty(BigDecimal reorderQty, BigDecimal reorderPoint, BigDecimal onHandPlusOnOrder) {
        if (reorderQty != null && reorderQty.signum() > 0) return reorderQty;
        BigDecimal target = reorderPoint.multiply(BigDecimal.valueOf(2));
        return target.subtract(onHandPlusOnOrder).max(BigDecimal.ZERO);
    }

    public static RequisitionType parseType(String raw) {
        try { return RequisitionType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
