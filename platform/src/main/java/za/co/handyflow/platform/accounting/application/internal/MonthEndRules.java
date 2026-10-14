package za.co.handyflow.platform.accounting.application.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Month-end close checklist (finance patch F13). Pure, so it is tested without Spring.
 * <p>
 * The checks the system can make itself (drafts, posting problems, bank sign-off, VAT, a trial balance that balances) and
 * the steps only a person can vouch for (accruals, payroll, depreciation, debtors, creditors). Closing sets the books'
 * lock date (F5) and is only allowed when every blocking item is done.
 */
public final class MonthEndRules {

    private MonthEndRules() {}

    public static final String DONE = "DONE", BLOCKED = "BLOCKED", WARNING = "WARNING", WAITING = "WAITING";

    /** A step the person has to confirm, with the text shown. */
    public record ManualStep(String key, String title) {}
    public static final List<ManualStep> MANUAL_STEPS = List.of(
            new ManualStep("ACCRUALS", "Accruals and prepayments reviewed and posted"),
            new ManualStep("PAYROLL", "Payroll for the month posted"),
            new ManualStep("DEPRECIATION", "Depreciation posted"),
            new ManualStep("DEBTORS", "Debtors reviewed against customer statements"),
            new ManualStep("CREDITORS", "Creditors reviewed against supplier statements"));

    public record Item(String key, String title, String kind, String status, String detail, boolean blocking) {}

    public record BankState(String name, LocalDate signedOffThrough, boolean hasLines) {}

    public static boolean isMonthEnd(LocalDate d) {
        return d != null && d.equals(d.withDayOfMonth(d.lengthOfMonth()));
    }

    /** The usual date to close: the last day of the month before {@code today}. */
    public static LocalDate defaultPeriodEnd(LocalDate today) {
        return today.withDayOfMonth(1).minusDays(1);
    }

    private static String plural(long n, String one, String many) { return n + " " + (n == 1 ? one : many); }

    /** Done when there are none; otherwise blocked (or a warning), saying how many. */
    public static Item countItem(String key, String title, long count, String problemText, boolean blocking) {
        if (count <= 0) return new Item(key, title, "AUTO", DONE, "None", blocking);
        return new Item(key, title, "AUTO", blocking ? BLOCKED : WARNING, problemText.replace("{n}", String.valueOf(count)), blocking);
    }

    /** Every bank account that has statement lines needs a sign-off covering the period end. */
    public static Item bankItem(LocalDate periodEnd, List<BankState> banks) {
        List<String> open = new ArrayList<>();
        for (BankState b : banks) {
            if (!b.hasLines()) continue;
            if (b.signedOffThrough() == null || b.signedOffThrough().isBefore(periodEnd)) open.add(b.name());
        }
        String title = "Bank reconciliations signed off";
        if (open.isEmpty()) return new Item("BANK", title, "AUTO", DONE, "Every bank account with a statement is signed off", true);
        return new Item("BANK", title, "AUTO", BLOCKED, "Not signed off to " + periodEnd + ": " + String.join(", ", open), true);
    }

    /** The trial balance for the whole ledger up to the date must balance to the cent. */
    public static Item trialBalanceItem(java.math.BigDecimal debits, java.math.BigDecimal credits) {
        java.math.BigDecimal diff = debits.subtract(credits).abs();
        String title = "Trial balance balances";
        if (diff.compareTo(new java.math.BigDecimal("0.005")) < 0) return new Item("TRIAL_BALANCE", title, "AUTO", DONE, "Debits equal credits", true);
        return new Item("TRIAL_BALANCE", title, "AUTO", BLOCKED, "Debits and credits differ by R " + diff.setScale(2, java.math.RoundingMode.HALF_UP), true);
    }

    public static Item manualItem(ManualStep step, boolean confirmed, String detail) {
        return new Item(step.key(), step.title(), "MANUAL", confirmed ? DONE : WAITING,
                confirmed ? detail : "Not confirmed yet", true);
    }

    public static Item lockItem(LocalDate periodEnd, LocalDate lockedThrough) {
        boolean done = lockedThrough != null && !lockedThrough.isBefore(periodEnd);
        return new Item("LOCK", "Books closed", "AUTO", done ? DONE : WAITING,
                done ? "Closed up to and including " + lockedThrough : "Closing sets the lock date to " + periodEnd, false);
    }

    /** Blocking items that are not done, as short titles. */
    public static List<String> outstanding(List<Item> items) {
        List<String> out = new ArrayList<>();
        for (Item i : items) {
            if (i.blocking() && !DONE.equals(i.status())) out.add(i.title());
        }
        return out;
    }

    /** Null when the books may be closed to {@code periodEnd}, otherwise why not. */
    public static String closeProblem(LocalDate periodEnd, LocalDate today, LocalDate lockedThrough, List<Item> items) {
        if (periodEnd == null) return "Choose the month to close";
        if (!isMonthEnd(periodEnd)) return "Close to the last day of a month";
        if (periodEnd.isAfter(today)) return "A month cannot be closed before it has ended";
        if (lockedThrough != null && !periodEnd.isAfter(lockedThrough))
            return "The books are already closed up to " + lockedThrough;
        List<String> open = outstanding(items);
        if (!open.isEmpty()) return plural(open.size(), "item is", "items are") + " not done: " + String.join("; ", open);
        return null;
    }

    /** Null when a manual step may be confirmed or taken back for this period, otherwise why not. */
    public static String confirmProblem(String key, LocalDate periodEnd, LocalDate lockedThrough) {
        boolean known = MANUAL_STEPS.stream().anyMatch(s -> s.key().equals(key));
        if (!known) return "Unknown step";
        if (!isMonthEnd(periodEnd)) return "Use the last day of a month";
        if (lockedThrough != null && !periodEnd.isAfter(lockedThrough))
            return "The books are closed up to " + lockedThrough + ". Reopen the period to change this";
        return null;
    }
}
