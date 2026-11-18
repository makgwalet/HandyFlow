package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Pure rules behind the procurement policy refinements. Spring-free so they are unit tested without a context.
 * <ul>
 *   <li>A catalogue category's own price tolerance beats the company's; a line with no category uses the company's.</li>
 *   <li>A supplier's own VAT recoverability beats the company's (a non-VAT-vendor supplier recovers 0 %).</li>
 *   <li>Money in a foreign currency is measured against budgets in the company's base currency at the rate in force
 *       on the document date; no rate means no figure, never "1".</li>
 * </ul>
 */
public final class ScmPolicyRules {

    private ScmPolicyRules() {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static BigDecimal tolerance(BigDecimal companyPct, BigDecimal categoryPct) {
        return categoryPct != null ? categoryPct : companyPct;
    }

    public static BigDecimal vatRecoverablePct(BigDecimal companyPct, BigDecimal supplierPct) {
        return supplierPct != null ? supplierPct : companyPct;
    }

    /** Share of a document's VAT that counts towards a budget, 0..1, for the given recoverable percentage. */
    public static BigDecimal vatFactor(String basis, BigDecimal recoverablePct, boolean includeNonRecoverable) {
        BigDecimal unrecoverable = HUNDRED.subtract(recoverablePct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        if (ProcurementPolicy.INC_VAT.equals(basis)) return BigDecimal.ONE.setScale(4);
        if (ProcurementPolicy.NET_OF_RECOVERABLE.equals(basis)) return unrecoverable;
        return includeNonRecoverable ? unrecoverable : BigDecimal.ZERO.setScale(4);
    }

    /** Amount in the base currency, 2 dp. A null rate is a programming error here: callers decide what "no rate" means. */
    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        if (rate == null) throw new IllegalArgumentException("No exchange rate");
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isBase(String currency, String baseCurrency) {
        return currency == null || currency.isBlank() || currency.trim().equalsIgnoreCase(baseCurrency);
    }

    /** What the extra cost of a price increase is: quantity x (actual - estimate), never negative. */
    public static BigDecimal increaseAmount(BigDecimal estimate, BigDecimal actual, BigDecimal qty) {
        BigDecimal est = estimate != null ? estimate : BigDecimal.ZERO;
        BigDecimal diff = actual.subtract(est);
        return diff.signum() <= 0 ? BigDecimal.ZERO : diff.multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    public static String currencyProblem(String code) {
        if (code == null || !code.trim().matches("[A-Za-z]{3}")) return "Currency must be a 3-letter code such as USD";
        return null;
    }

    public static String rateProblem(String currency, String baseCurrency, BigDecimal rate) {
        String p = currencyProblem(currency);
        if (p != null) return p;
        if (isBase(currency, baseCurrency)) return currency.trim().toUpperCase(Locale.ROOT) + " is the base currency and always has a rate of 1";
        if (rate == null || rate.signum() <= 0) return "The rate must be greater than zero";
        if (rate.scale() > 8) return "The rate can have at most 8 decimal places";
        return null;
    }

    public static String percentProblem(BigDecimal pct, String what) {
        if (pct == null || pct.signum() < 0 || pct.compareTo(HUNDRED) > 0) return what + " must be between 0 and 100 %";
        return null;
    }

    /**
     * Contract savings on one PO line. {@code list} is the catalogue list price, {@code contract} the agreed contract
     * price (null when no contract covers the item), {@code paid} what the PO line cost. saving = (list - paid) x qty
     * when positive; leakage = (paid - contract) x qty when the PO paid more than a covering contract allowed.
     */
    public record Savings(BigDecimal saving, BigDecimal leakage) {}

    public static Savings savings(BigDecimal list, BigDecimal contract, BigDecimal paid, BigDecimal qty) {
        BigDecimal saving = BigDecimal.ZERO, leakage = BigDecimal.ZERO;
        if (list != null && list.signum() > 0 && paid.compareTo(list) < 0)
            saving = list.subtract(paid).multiply(qty).setScale(2, RoundingMode.HALF_UP);
        if (contract != null && paid.compareTo(contract) > 0)
            leakage = paid.subtract(contract).multiply(qty).setScale(2, RoundingMode.HALF_UP);
        return new Savings(saving, leakage);
    }
}
