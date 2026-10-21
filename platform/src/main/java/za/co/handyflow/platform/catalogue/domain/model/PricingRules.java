package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure pricing rules: price list codes, effective dates, the minimum-price floor, the maker-checker rule,
 * margin and VAT arithmetic. No Spring, no database. Rejections are IllegalArgumentException with a message
 * fit to show the user (the maker-checker refusal is {@link SelfApprovalException}).
 */
public final class PricingRules {
    private PricingRules() {}

    public static final int MAX_CODE = 30, MAX_LIST_NAME = 60, MAX_REASON = 255, MAX_DAYS_AHEAD = 730;
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** The proposer may not decide their own change. Mapped to HTTP 403 by the service. */
    public static final class SelfApprovalException extends RuntimeException {
        public SelfApprovalException(String message) { super(message); }
    }

    public static String listCode(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) throw new IllegalArgumentException("The price list code is required.");
        if (s.length() > MAX_CODE) throw new IllegalArgumentException("The price list code can be at most " + MAX_CODE + " characters.");
        if (!CODE.matcher(s).matches()) throw new IllegalArgumentException("The price list code must start with a letter and use only letters, numbers and underscores.");
        return s;
    }

    public static String listName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("The price list name is required.");
        if (s.length() > MAX_LIST_NAME) throw new IllegalArgumentException("The price list name can be at most " + MAX_LIST_NAME + " characters.");
        return s;
    }

    /** No date means "today". A change cannot start in the past, nor more than two years ahead. */
    public static LocalDate effectiveDate(LocalDate requested, LocalDate today) {
        if (requested == null) return today;
        if (requested.isBefore(today)) throw new IllegalArgumentException("The effective date cannot be in the past.");
        if (requested.isAfter(today.plusDays(MAX_DAYS_AHEAD))) throw new IllegalArgumentException("The effective date can be at most two years ahead.");
        return requested;
    }

    /** The reason is optional unless an approver has to read it (then at least 3 characters). */
    public static String reason(String raw, boolean required) {
        String s = raw == null ? "" : raw.trim();
        if (required && s.length() < 3) throw new IllegalArgumentException("Say why the price is changing (at least 3 characters).");
        if (s.length() > MAX_REASON) throw new IllegalArgumentException("The reason can be at most " + MAX_REASON + " characters.");
        return s.isEmpty() ? null : s;
    }

    /** A rejection or cancellation note: required for a rejection so the proposer knows why. */
    public static String note(String raw, boolean required) {
        String s = raw == null ? "" : raw.trim();
        if (required && s.length() < 3) throw new IllegalArgumentException("Say why (at least 3 characters).");
        if (s.length() > MAX_REASON) throw new IllegalArgumentException("The note can be at most " + MAX_REASON + " characters.");
        return s.isEmpty() ? null : s;
    }

    /** A price on any list must not be below the item's minimum price (when one is set). */
    public static void checkAboveMinimum(BigDecimal price, BigDecimal min) {
        if (price != null && min != null && price.compareTo(min) < 0)
            throw new IllegalArgumentException("The price cannot be below the minimum price of " + money(min) + ".");
    }

    /** The decider must be someone other than the proposer; an unknown user cannot decide at all. */
    public static void checkMakerChecker(UUID proposer, UUID decider) {
        if (decider == null) throw new SelfApprovalException("Your sign-in cannot approve changes.");
        if (decider.equals(proposer)) throw new SelfApprovalException("You proposed this change, so someone else has to decide it.");
    }

    /** Margin on price, in percent to one decimal; null when it cannot be worked out. */
    public static BigDecimal marginPercent(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.signum() <= 0) return null;
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    /** Change from old to new in percent to one decimal; null when there is no old price to compare with. */
    public static BigDecimal changePercent(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null || oldPrice.signum() <= 0) return null;
        return newPrice.subtract(oldPrice).multiply(BigDecimal.valueOf(100)).divide(oldPrice, 1, RoundingMode.HALF_UP);
    }

    /** Price including VAT, rounded to cents; an exempt item carries no VAT. */
    public static BigDecimal withVat(BigDecimal price, BigDecimal vatRatePercent, boolean exempt) {
        if (price == null) return null;
        if (exempt || vatRatePercent == null) return price.setScale(2, RoundingMode.HALF_UP);
        return price.multiply(BigDecimal.ONE.add(vatRatePercent.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
    }

    public static String money(BigDecimal v) { return v == null ? "—" : "R " + v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    /** "price: 100.00 -> 120.00 (+20.0%) on Retail, from 2026-11-01" for the audit trail. */
    public static String describe(String listName, BigDecimal oldPrice, BigDecimal newPrice, LocalDate from) {
        BigDecimal pct = changePercent(oldPrice, newPrice);
        return listName + ": " + (oldPrice == null ? "(none)" : oldPrice.setScale(2, RoundingMode.HALF_UP).toPlainString())
                + " -> " + newPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + (pct == null ? "" : " (" + (pct.signum() >= 0 ? "+" : "") + pct.toPlainString() + "%)")
                + ", from " + from;
    }
}
