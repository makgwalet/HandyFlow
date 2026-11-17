package za.co.handyflow.platform.supplychain.application.internal;

import za.co.handyflow.platform.supplychain.domain.enums.BudgetControlMode;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure budget evaluation. Each applicable budget is tested independently — a spend tagged with a
 * department AND a project must fit in BOTH. used = what the budget already carries (actual invoiced
 * or still-committed, whichever is larger, per PO), excluding the document being checked.
 * <ul>
 *   <li>EXCEEDED  used + requested &gt; amount</li>
 *   <li>NEAR_LIMIT used + requested &gt;= nearLimitPct of amount (but not exceeded)</li>
 * </ul>
 * NONE-mode budgets are tracked elsewhere but never produce findings here.
 */
public final class BudgetRules {

    private BudgetRules() {}

    public enum State { OK, NEAR_LIMIT, EXCEEDED }

    public record View(UUID budgetId, String name, BudgetScopeType scope, String scopeValue,
                       BigDecimal amount, BudgetControlMode mode, BigDecimal used) {}

    public record Finding(UUID budgetId, String name, BudgetScopeType scope, String scopeValue,
                          BudgetControlMode mode, BigDecimal amount, BigDecimal used, BigDecimal requested,
                          BigDecimal availableAfter, State state, String message) {}

    public record Result(List<Finding> findings) {
        /** An exceeded HARD_STOP budget — blocks unless an authorised override covers the amount. */
        public boolean hardStop() {
            return findings.stream().anyMatch(f -> f.state() == State.EXCEEDED && f.mode() == BudgetControlMode.HARD_STOP);
        }
        public List<Finding> warnings() {
            return findings.stream().filter(f -> f.state() != State.OK).toList();
        }
        public boolean hasWarnings() { return !warnings().isEmpty(); }
        public String message() {
            return String.join("; ", warnings().stream().map(Finding::message).toList());
        }
    }

    /** Does a budget with this scope apply to a spend carrying these dimensions? */
    public static boolean matches(BudgetScopeType type, String scopeValue, String department, String costCentre, String project) {
        String dim = switch (type) {
            case DEPARTMENT -> department;
            case COST_CENTRE -> costCentre;
            case PROJECT -> project;
        };
        return dim != null && scopeValue != null && dim.trim().equalsIgnoreCase(scopeValue.trim());
    }

    public static Result evaluate(List<View> budgets, BigDecimal requested, BigDecimal nearLimitPct) {
        List<Finding> out = new ArrayList<>();
        BigDecimal nearFactor = nearLimitPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        for (View b : budgets) {
            if (b.mode() == BudgetControlMode.NONE) continue;
            BigDecimal after = b.used().add(requested);
            BigDecimal available = b.amount().subtract(after);
            State state = available.signum() < 0 ? State.EXCEEDED
                    : after.compareTo(b.amount().multiply(nearFactor)) >= 0 ? State.NEAR_LIMIT : State.OK;
            String label = b.scope().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " '" + b.scopeValue() + "' budget '" + b.name() + "'";
            String msg = switch (state) {
                case EXCEEDED -> label + ": R " + money(b.used()) + " used + R " + money(requested) + " requested exceeds the R "
                        + money(b.amount()) + " budget by R " + money(available.negate());
                case NEAR_LIMIT -> label + ": this would take it to R " + money(after) + " of R " + money(b.amount());
                case OK -> "";
            };
            out.add(new Finding(b.budgetId(), b.name(), b.scope(), b.scopeValue(), b.mode(), b.amount(), b.used(),
                    requested, available, state, msg));
        }
        return new Result(out);
    }

    private static String money(BigDecimal v) { return String.format(Locale.ROOT, "%.2f", v); }
}
