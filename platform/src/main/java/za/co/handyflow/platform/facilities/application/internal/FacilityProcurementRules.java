package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityGoodsReceiptLine;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrderLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The arithmetic of buying: three-way matching (ordered, received, invoiced) and approval limits. Pure. */
public final class FacilityProcurementRules {

    /** Billing may differ from what was received by the larger of this much, or 1% of the received value. */
    public static final BigDecimal TOLERANCE_MINIMUM = new BigDecimal("5.00");
    public static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.01");

    private FacilityProcurementRules() {}

    /**
     * @param status NOT_INVOICED, NOT_RECEIVED (billed with nothing delivered), MATCHED, OVER_INVOICED (billed more than
     *               was delivered) or UNDER_INVOICED (billed less so far — normal until the rest is invoiced)
     */
    public record Match(BigDecimal ordered, BigDecimal received, BigDecimal invoiced, BigDecimal variance,
                        BigDecimal tolerance, String status) {
        /** Approving an invoice in this state needs an explanation. */
        public boolean needsExplanation() { return "NOT_RECEIVED".equals(status) || "OVER_INVOICED".equals(status); }
    }

    public static Match evaluate(BigDecimal ordered, BigDecimal received, BigDecimal invoiced) {
        BigDecimal rec = money(received);
        BigDecimal inv = money(invoiced);
        BigDecimal tolerance = TOLERANCE_MINIMUM.max(rec.multiply(TOLERANCE_RATE).setScale(2, RoundingMode.HALF_UP));
        BigDecimal variance = inv.subtract(rec);
        String status;
        if (inv.signum() == 0) status = "NOT_INVOICED";
        else if (rec.signum() == 0) status = "NOT_RECEIVED";
        else if (variance.abs().compareTo(tolerance) <= 0) status = "MATCHED";
        else if (variance.signum() > 0) status = "OVER_INVOICED";
        else status = "UNDER_INVOICED";
        return new Match(money(ordered), rec, inv, variance, tolerance, status);
    }

    /**
     * What has been received, in money. Uses the cost booked on each goods receipt; any quantity received before receipts
     * were recorded is valued at the order price.
     */
    public static BigDecimal receivedValue(Collection<FacilityPurchaseOrderLine> orderLines, Collection<FacilityGoodsReceiptLine> receiptLines) {
        Map<UUID, BigDecimal> documentedQty = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FacilityGoodsReceiptLine r : receiptLines) {
            documentedQty.merge(r.getPoLineId(), r.getQuantity(), BigDecimal::add);
            total = total.add(r.lineValue());
        }
        for (FacilityPurchaseOrderLine l : orderLines) {
            BigDecimal undocumented = l.getReceivedQty().subtract(documentedQty.getOrDefault(l.getId(), BigDecimal.ZERO));
            if (undocumented.signum() > 0) total = total.add(undocumented.multiply(l.getUnitCost()));
        }
        return money(total);
    }

    /**
     * Whether an approver may approve an order of this value; null when they may. Admins are never limited. Until any
     * limit has been set for the tenant, nobody is limited (the long-standing behaviour); once one exists, anyone without
     * a limit of their own cannot approve.
     */
    public static String approvalProblem(boolean admin, boolean anyLimitsConfigured, BigDecimal approverLimit, BigDecimal orderTotal) {
        if (admin || !anyLimitsConfigured) return null;
        if (approverLimit == null) return "You have no purchase order approval limit, so you cannot approve orders. Ask an administrator to set one";
        if (orderTotal.compareTo(approverLimit) > 0)
            return "This order is R" + money(orderTotal).toPlainString() + ", above your approval limit of R" + money(approverLimit).toPlainString()
                    + ". Someone with a higher limit must approve it";
        return null;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
