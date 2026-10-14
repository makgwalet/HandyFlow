package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure rules for budgets: 12 months from a first-of-month start, income and expense accounts only. */
public final class BudgetRules {
    private BudgetRules() {}

    public static final int MONTHS = 12;
    public static final int MAX_LINES = 2000;

    public record LineIn(UUID accountId, int month, BigDecimal amount) {}
    public record Variance(BigDecimal budget, BigDecimal actual, BigDecimal amount, BigDecimal percent, boolean favourable) {}

    public static String createProblem(String name, LocalDate start) {
        if (name == null || name.isBlank()) return "Give the budget a name.";
        if (name.trim().length() > 120) return "The name is too long (120 characters at most).";
        if (start == null) return "Choose the month the budget starts.";
        if (start.getDayOfMonth() != 1) return "A budget starts on the first day of a month.";
        return null;
    }

    public static List<LocalDate> months(LocalDate start) {
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) out.add(start.plusMonths(i));
        return out;
    }

    /** accountTypes maps account id to INCOME / EXPENSE / other; unknown ids are not in the map. */
    public static String linesProblem(List<LineIn> lines, java.util.Map<UUID, String> accountTypes) {
        if (lines == null) return "Send the budget lines.";
        if (lines.size() > MAX_LINES) return "At most " + MAX_LINES + " lines at once.";
        Set<String> seen = new java.util.HashSet<>();
        for (LineIn l : lines) {
            if (l.accountId() == null || !accountTypes.containsKey(l.accountId())) return "A line names an account that does not exist.";
            String type = accountTypes.get(l.accountId());
            if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) return "Budgets are for income and expense accounts only.";
            if (l.month() < 0 || l.month() >= MONTHS) return "Month must be 0 to 11.";
            if (l.amount() == null || l.amount().signum() < 0) return "Budget amounts cannot be negative.";
            if (l.amount().scale() > 2 && l.amount().stripTrailingZeros().scale() > 2) return "Amounts have at most two decimals.";
            if (!seen.add(l.accountId() + ":" + l.month())) return "An account appears twice for the same month.";
        }
        return null;
    }

    /** status DRAFT may be edited; only a draft with lines may be activated, and only one active budget per start month. */
    public static String activateProblem(String status, int lineCount, boolean otherActiveForSameStart) {
        if (!"DRAFT".equals(status)) return "Only a draft budget can be activated.";
        if (lineCount == 0) return "Add at least one line before activating.";
        if (otherActiveForSameStart) return "Another budget is already active for this year. Close it first.";
        return null;
    }

    public static String editProblem(String status) {
        return "DRAFT".equals(status) ? null : "An active or closed budget cannot be changed. Make a new budget.";
    }

    /** Income beats budget when actual is higher; expense beats budget when actual is lower. */
    public static Variance variance(String accountType, BigDecimal budget, BigDecimal actual) {
        BigDecimal b = budget == null ? BigDecimal.ZERO : budget;
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        boolean income = "INCOME".equals(accountType);
        BigDecimal diff = income ? a.subtract(b) : b.subtract(a);
        BigDecimal pct = b.signum() == 0 ? null : diff.multiply(new BigDecimal("100")).divide(b, 1, RoundingMode.HALF_UP);
        return new Variance(b, a, diff, pct, diff.signum() >= 0);
    }

    /** Last year's monthly actuals plus an uplift. Index 0 is the first month of the new budget. */
    public static List<BigDecimal> fromActuals(List<BigDecimal> lastYear, BigDecimal upliftPercent) {
        BigDecimal factor = BigDecimal.ONE.add((upliftPercent == null ? BigDecimal.ZERO : upliftPercent).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            BigDecimal v = i < lastYear.size() && lastYear.get(i) != null ? lastYear.get(i) : BigDecimal.ZERO;
            out.add(v.max(BigDecimal.ZERO).multiply(factor).setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    public static String upliftProblem(BigDecimal uplift) {
        if (uplift == null) return null;
        if (uplift.compareTo(new BigDecimal("-90")) < 0 || uplift.compareTo(new BigDecimal("500")) > 0) return "Uplift must be between -90% and 500%.";
        return null;
    }
}
