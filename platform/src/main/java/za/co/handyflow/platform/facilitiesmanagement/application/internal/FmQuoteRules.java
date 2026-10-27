package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuoteLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

/** Quote arithmetic and limits. Pure: no persistence, no clock. */
public final class FmQuoteRules {

    /** The longest a quote can stay open for the client. */
    public static final int MAX_VALIDITY_DAYS = 90;
    /** A sanity limit on one quote, to catch a misplaced decimal point rather than to police pricing. */
    public static final BigDecimal MAX_TOTAL = new BigDecimal("10000000.00");

    private FmQuoteRules() {}

    public record Totals(BigDecimal subtotal, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal total) {}

    /** Lines are VAT-exclusive; VAT is added on the subtotal at {@code settings}' effective rate. */
    public static Totals totals(Collection<FmQuoteLine> lines, FmBillingSettings settings) {
        BigDecimal subtotal = lines.stream().map(FmQuoteLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = settings.vatOn(subtotal);
        BigDecimal total = subtotal.add(vat);
        if (total.compareTo(MAX_TOTAL) > 0)
            throw new IllegalArgumentException("A quote cannot be more than R" + MAX_TOTAL.toPlainString() + " in total");
        return new Totals(subtotal, settings.effectiveVatRate(), vat, total);
    }

    /** The date a quote may be valid until: today or later, and at most {@link #MAX_VALIDITY_DAYS} days ahead. */
    public static void requireValidity(LocalDate validUntil, LocalDate today) {
        if (validUntil.isBefore(today)) throw new IllegalArgumentException("The valid-until date cannot be in the past");
        if (validUntil.isAfter(today.plusDays(MAX_VALIDITY_DAYS)))
            throw new IllegalArgumentException("A quote can be open for at most " + MAX_VALIDITY_DAYS + " days");
    }

    /** What to show: a SENT quote past its validity reads EXPIRED even before the sweep has recorded it. */
    public static String effectiveStatus(FmQuote q, LocalDate today) {
        return q.isExpiredOn(today) ? "EXPIRED" : q.getStatus();
    }
}
