package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** How a hire charge is worked out and what it may cover. Pure rules: no database, no clock. */
public final class HireRules {
    private HireRules() {}

    public static final String DAILY = "DAILY";
    public static final String HOURLY = "HOURLY";
    public static final List<String> BASES = List.of(DAILY, HOURLY);
    public static final String PENDING = "PENDING";
    public static final String INVOICED = "INVOICED";
    public static final String VOIDED = "VOIDED";
    /** South African VAT, used when a charge does not say otherwise. */
    public static final BigDecimal DEFAULT_VAT = new BigDecimal("15.00");
    public static final int MAX_PERIOD_DAYS = 366;

    public static String basis(String basis) {
        if (basis == null || !BASES.contains(basis.trim().toUpperCase()))
            throw new IllegalArgumentException("Bill by DAILY (days on hire) or HOURLY (hours worked).");
        return basis.trim().toUpperCase();
    }

    /** Calendar days from the first to the last day, both counted. */
    public static int days(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * The period must sit inside the time the machine was on the deployment: not before it started, not after it ended,
     * and not in the future if it is still out.
     */
    public static void checkPeriod(LocalDate from, LocalDate to, LocalDate deployedOn, LocalDate returnedOn, LocalDate today) {
        if (from == null || to == null) throw new IllegalArgumentException("Give the first and last day to charge.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day cannot be before the first day.");
        if (from.isBefore(deployedOn)) throw new IllegalArgumentException("The machine only went on hire on " + deployedOn + ".");
        LocalDate lastDay = returnedOn != null ? returnedOn : today;
        if (to.isAfter(lastDay))
            throw new IllegalArgumentException(returnedOn != null
                    ? "The machine came back on " + returnedOn + ". Charge up to that day."
                    : "You can only charge up to today (" + today + ") while the machine is still on hire.");
        if (days(from, to) > MAX_PERIOD_DAYS) throw new IllegalArgumentException("A charge covers at most " + MAX_PERIOD_DAYS + " days. Split it.");
    }

    public static BigDecimal vatRate(BigDecimal vat) {
        if (vat == null) return DEFAULT_VAT;
        if (vat.signum() < 0 || vat.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("VAT must be between 0 and 100.");
        return vat.setScale(2, RoundingMode.HALF_UP);
    }

    /** The rate for this basis, or a message saying which rate to set. */
    public static BigDecimal rateFor(String basis, BigDecimal dailyRate, BigDecimal hourlyRate) {
        BigDecimal rate = DAILY.equals(basis) ? dailyRate : hourlyRate;
        if (rate == null || rate.signum() <= 0)
            throw new IllegalArgumentException("Set the machine's " + (DAILY.equals(basis) ? "daily" : "hourly") + " rate first.");
        return rate;
    }

    /** The machine's own rate, or, when one is agreed for this charge, that rate. A different rate always needs a reason. */
    public static BigDecimal rate(String basis, BigDecimal dailyRate, BigDecimal hourlyRate, BigDecimal agreed, String reason) {
        if (agreed == null) return rateFor(basis, dailyRate, hourlyRate);
        if (agreed.signum() <= 0) throw new IllegalArgumentException("The agreed rate must be more than nothing.");
        EarthmovingRules.requireReason(reason, "charging a different rate");
        return agreed;
    }

    /** Hours worked in the period: needs at least some. */
    public static BigDecimal hours(BigDecimal worked) {
        if (worked == null || worked.signum() <= 0) throw new IllegalArgumentException("No completed shifts were logged on this machine in that period.");
        return worked.setScale(2, RoundingMode.HALF_UP);
    }

    /** Amount excluding VAT, to the cent. */
    public static BigDecimal amount(BigDecimal quantity, BigDecimal rate) {
        return quantity.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal vatAmount(BigDecimal amount, BigDecimal vatRate) {
        return amount.multiply(vatRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        return !aFrom.isAfter(bTo) && !bFrom.isAfter(aTo);
    }

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** The text that appears on the invoice line. */
    public static String describe(String machine, String site, String basis, LocalDate from, LocalDate to, BigDecimal quantity, BigDecimal rate) {
        String span = from.equals(to) ? D.format(from) : D.format(from) + " to " + D.format(to);
        String qty = DAILY.equals(basis) ? quantity.stripTrailingZeros().toPlainString() + (quantity.compareTo(BigDecimal.ONE) == 0 ? " day" : " days")
                : quantity.stripTrailingZeros().toPlainString() + " hrs";
        return "Hire: " + machine + (site == null || site.isBlank() ? "" : " at " + site) + ", " + span + " (" + qty + " @ R" + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
    }
}
