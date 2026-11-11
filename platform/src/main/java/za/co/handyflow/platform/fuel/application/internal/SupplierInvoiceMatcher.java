package za.co.handyflow.platform.fuel.application.internal;

import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Three-way match of a supplier invoice against the purchase order and the receipts it covers.
 * Litres must agree with what was actually received (0.5% tolerance, for meter and dip rounding); the price must agree with
 * the price booked at receiving and with the purchase order price (half a cent a litre). Nothing is adjusted silently: every
 * difference is listed so a person decides.
 */
public final class SupplierInvoiceMatcher {

    public static final BigDecimal LITRES_TOLERANCE = new BigDecimal("0.005");
    public static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.005");

    private SupplierInvoiceMatcher() {}

    public record Result(String status, BigDecimal litresReceived, BigDecimal receiptValue, BigDecimal litresVariance,
                         BigDecimal goodsValue, BigDecimal valueVariance, BigDecimal priceVsPo, List<String> issues) {}

    /** @param receipts the receipts linked to the invoice (reversed ones are listed as an issue and excluded from the totals); po may be null */
    public static Result match(BigDecimal invLitres, BigDecimal invPrice, List<FuelReceipt> receipts, FuelPurchaseOrder po) {
        List<String> issues = new ArrayList<>();
        BigDecimal litres = BigDecimal.ZERO, value = BigDecimal.ZERO;
        int reversed = 0;
        for (FuelReceipt r : receipts) {
            if (r.isReversed()) { reversed++; continue; }
            litres = litres.add(r.getLitresReceived());
            value = value.add(r.getTotalCost());
        }
        BigDecimal goods = invLitres.multiply(invPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal litresVar = invLitres.subtract(litres);
        BigDecimal valueVar = goods.subtract(value);
        BigDecimal priceVsPo = po == null ? null : invPrice.subtract(po.getPricePerLitre());

        if (reversed > 0) issues.add(reversed + " linked receipt" + (reversed == 1 ? " was" : "s were") + " reversed after capture, so that fuel no longer counts as received.");
        if (litres.signum() <= 0) {
            issues.add("No received fuel is linked to this invoice, so the litres could not be checked.");
        } else {
            BigDecimal allowed = litres.multiply(LITRES_TOLERANCE);
            if (litresVar.abs().compareTo(allowed) > 0) {
                issues.add("Invoiced " + plain(invLitres) + " L but " + plain(litres) + " L was received (" + signed(litresVar) + " L).");
            }
            BigDecimal avg = value.divide(litres, 4, RoundingMode.HALF_UP);
            BigDecimal diff = invPrice.subtract(avg);
            if (diff.abs().compareTo(PRICE_TOLERANCE) > 0) {
                issues.add("Invoice price R " + plain(invPrice) + "/L is R " + diff.abs().setScale(4, RoundingMode.HALF_UP).toPlainString()
                        + (diff.signum() > 0 ? " above" : " below") + " the R " + avg.toPlainString() + "/L booked at receiving.");
            }
        }
        if (priceVsPo != null && priceVsPo.abs().compareTo(PRICE_TOLERANCE) > 0) {
            issues.add("Invoice price R " + plain(invPrice) + "/L is R " + priceVsPo.abs().setScale(4, RoundingMode.HALF_UP).toPlainString()
                    + (priceVsPo.signum() > 0 ? " above" : " below") + " the " + po.getPoNumber() + " price of R " + plain(po.getPricePerLitre()) + "/L.");
        }
        String status = litres.signum() <= 0 ? "UNMATCHED" : issues.isEmpty() ? "MATCHED" : "VARIANCE";
        return new Result(status, litres, value, litresVar, goods, valueVar, priceVsPo, List.copyOf(issues));
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
    private static String signed(BigDecimal v) { return (v.signum() > 0 ? "+" : "") + plain(v); }
}
