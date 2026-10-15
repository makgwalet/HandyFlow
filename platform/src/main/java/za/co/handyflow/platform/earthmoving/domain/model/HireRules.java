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
    public static final String KILOMETRE = "KILOMETRE";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";
    public static final List<String> BASES = List.of(DAILY, WEEKLY, MONTHLY, HOURLY, KILOMETRE);
    /** Where a charge's rate came from. */
    public static final String FROM_CONTRACT = "CONTRACT";
    public static final String FROM_ASSET = "ASSET";
    public static final String FROM_AGREED = "AGREED";
    public static final String PENDING = "PENDING";
    public static final String INVOICED = "INVOICED";
    public static final String VOIDED = "VOIDED";
    /** South African VAT, used when a charge does not say otherwise. */
    public static final BigDecimal DEFAULT_VAT = new BigDecimal("15.00");
    public static final int MAX_PERIOD_DAYS = 366;

    public static String basis(String basis) {
        if (basis == null || !BASES.contains(basis.trim().toUpperCase()))
            throw new IllegalArgumentException("Bill by DAILY (days on hire), WEEKLY (weeks), MONTHLY (months), HOURLY (hours worked) or KILOMETRE (kilometres driven).");
        return basis.trim().toUpperCase();
    }

    /** Calendar days from the first to the last day, both counted. */
    public static int days(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Weeks charged: every week started counts as a whole week (1 to 7 days is one week, 8 to 14 is two). */
    public static int weeks(LocalDate from, LocalDate to) {
        return (days(from, to) + 6) / 7;
    }

    /**
     * Months charged: every month started counts as a whole month, counted from the first day. 1 Oct to 31 Oct is one month,
     * 1 Oct to 1 Nov is two. Each month is counted from the first day, so a start on the 31st does not drift.
     */
    public static int months(LocalDate from, LocalDate to) {
        LocalDate end = to.plusDays(1);
        int n = 1;
        while (from.plusMonths(n).isBefore(end)) n++;
        return n;
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

    /** The rate for this basis, or a message saying which rate to set. Kilometre, weekly and monthly rates come from the contract or are agreed per charge. */
    public static BigDecimal rateFor(String basis, BigDecimal dailyRate, BigDecimal hourlyRate) {
        if (KILOMETRE.equals(basis)) throw new IllegalArgumentException("Give the agreed rate per kilometre, with a reason.");
        if (WEEKLY.equals(basis) || MONTHLY.equals(basis))
            throw new IllegalArgumentException("Give the agreed rate per " + (WEEKLY.equals(basis) ? "week" : "month") + ", with a reason, or set it on the hire contract.");
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

    /** A rate and where it came from. */
    public record Rate(BigDecimal value, String source) {}

    /**
     * The rate to charge: one agreed for this charge (needs a reason), else the rate held on the hire contract, else the
     * machine's own daily or hourly rate. Weekly, monthly and kilometre rates exist only on a contract or per charge.
     */
    public static Rate choose(String basis, BigDecimal agreed, String reason, BigDecimal contractRate, BigDecimal dailyRate, BigDecimal hourlyRate) {
        if (agreed != null) return new Rate(rate(basis, dailyRate, hourlyRate, agreed, reason), FROM_AGREED);
        if (contractRate != null && contractRate.signum() > 0) return new Rate(contractRate, FROM_CONTRACT);
        return new Rate(rateFor(basis, dailyRate, hourlyRate), FROM_ASSET);
    }

    /** The rate held on a contract must be more than nothing. */
    public static BigDecimal contractRate(String basis, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("The " + word(basis) + " rate must be more than nothing.");
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    /** The unit as people say it in a sentence: "per day". */
    public static String word(String basis) {
        return switch (basis) {
            case DAILY -> "daily"; case WEEKLY -> "weekly"; case MONTHLY -> "monthly"; case KILOMETRE -> "kilometre"; default -> "hourly";
        };
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
        String n = quantity.stripTrailingZeros().toPlainString();
        boolean one = quantity.compareTo(BigDecimal.ONE) == 0;
        String qty = DAILY.equals(basis) ? n + (one ? " day" : " days")
                : WEEKLY.equals(basis) ? n + (one ? " week" : " weeks")
                : MONTHLY.equals(basis) ? n + (one ? " month" : " months")
                : n + " hrs";
        return "Hire: " + machine + (site == null || site.isBlank() ? "" : " at " + site) + ", " + span + " (" + qty + " @ R" + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
    }

    /**
     * Kilometres to charge: end odometer minus start odometer, less any allowance, never below nothing. Readings that go
     * backwards are refused rather than guessed at, and a period with nothing left to charge says so.
     */
    public static BigDecimal kilometres(BigDecimal start, BigDecimal end, BigDecimal included) {
        if (start == null || end == null) throw new IllegalArgumentException("The machine needs an odometer reading before and at the end of that period.");
        if (end.compareTo(start) < 0) throw new IllegalArgumentException("The odometer reading at the end (" + end.stripTrailingZeros().toPlainString()
                + ") is lower than at the start (" + start.stripTrailingZeros().toPlainString() + "). Correct the reading first.");
        if (included != null && included.signum() < 0) throw new IllegalArgumentException("The included kilometres cannot be negative.");
        BigDecimal billable = end.subtract(start).subtract(included == null ? BigDecimal.ZERO : included);
        if (billable.signum() <= 0) throw new IllegalArgumentException("No kilometres to charge for that period"
                + (included != null && included.signum() > 0 ? " after the included " + included.stripTrailingZeros().toPlainString() + " km." : "."));
        return billable.setScale(2, RoundingMode.HALF_UP);
    }

    /** The invoice-line unit for a basis. */
    public static String unit(String basis) {
        return DAILY.equals(basis) ? "day" : WEEKLY.equals(basis) ? "week" : MONTHLY.equals(basis) ? "month" : KILOMETRE.equals(basis) ? "km" : "hour";
    }

    /** The line text for a kilometre charge, showing the readings it came from. */
    public static String describeKm(String machine, String site, LocalDate from, LocalDate to, BigDecimal start, BigDecimal end,
                                    BigDecimal included, BigDecimal quantity, BigDecimal rate) {
        String span = from.equals(to) ? D.format(from) : D.format(from) + " to " + D.format(to);
        String allowance = included != null && included.signum() > 0 ? ", " + included.stripTrailingZeros().toPlainString() + " km included" : "";
        return "Hire: " + machine + (site == null || site.isBlank() ? "" : " at " + site) + ", " + span + " (odometer "
                + start.stripTrailingZeros().toPlainString() + " to " + end.stripTrailingZeros().toPlainString() + allowance + "; "
                + quantity.stripTrailingZeros().toPlainString() + " km @ R" + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
    }
}
