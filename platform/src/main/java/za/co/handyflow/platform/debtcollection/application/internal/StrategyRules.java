package za.co.handyflow.platform.debtcollection.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for collection strategies (finance F23). A strategy applies to cases in a balance and days-overdue band and
 * lists steps by days overdue. Each step raises ONE work item for a person, once per case. Nothing here sends anything.
 */
public final class StrategyRules {
    private StrategyRules() {}

    public static final Set<String> ACTIONS = Set.of("CALL", "SEND_REMINDER", "SEND_DEMAND", "LEGAL_REVIEW", "REVIEW_WRITE_OFF", "OTHER");
    public static final Set<String> ACTIVE_STATUSES = Set.of("OPEN", "DEMAND_SENT");
    public static final int MAX_STEPS = 12;

    public record Step(UUID id, int afterDays, String action, String instruction) {}
    public record Strategy(UUID id, String name, int priority, BigDecimal minBalance, BigDecimal maxBalance,
                           Integer minDays, Integer maxDays, int quietDays, List<Step> steps) {}
    public record Decision(Step raise, List<Step> skipped) {}

    public static String problem(Strategy s) {
        if (s.name() == null || s.name().isBlank()) return "Give the strategy a name.";
        if (s.name().trim().length() > 100) return "The name is too long.";
        if (s.priority() < 1 || s.priority() > 999) return "Order must be between 1 and 999.";
        if (s.minBalance() != null && s.minBalance().signum() < 0) return "The smallest balance cannot be negative.";
        if (s.minBalance() != null && s.maxBalance() != null && s.maxBalance().compareTo(s.minBalance()) < 0) return "The largest balance is below the smallest.";
        if (s.minDays() != null && s.minDays() < 0) return "Days overdue cannot be negative.";
        if (s.minDays() != null && s.maxDays() != null && s.maxDays() < s.minDays()) return "The most days overdue is below the fewest.";
        if (s.quietDays() < 0 || s.quietDays() > 30) return "Days to wait after contact must be 0 to 30.";
        if (s.steps() == null || s.steps().isEmpty()) return "Add at least one step.";
        if (s.steps().size() > MAX_STEPS) return "At most " + MAX_STEPS + " steps.";
        Set<Integer> days = new HashSet<>();
        for (Step st : s.steps()) {
            if (!ACTIONS.contains(st.action())) return "Choose an action for every step.";
            if (st.afterDays() < 0 || st.afterDays() > 730) return "Days overdue for a step must be 0 to 730.";
            if (!days.add(st.afterDays())) return "Two steps start on the same day (" + st.afterDays() + ").";
            if (st.instruction() != null && st.instruction().length() > 300) return "A step's instruction is too long (300 characters).";
        }
        return null;
    }

    public static boolean matches(Strategy s, BigDecimal balance, long daysOverdue) {
        if (s.minBalance() != null && balance.compareTo(s.minBalance()) < 0) return false;
        if (s.maxBalance() != null && balance.compareTo(s.maxBalance()) > 0) return false;
        if (s.minDays() != null && daysOverdue < s.minDays()) return false;
        if (s.maxDays() != null && daysOverdue > s.maxDays()) return false;
        return true;
    }

    /** The first matching strategy in order (lowest order number, then name). */
    public static Strategy pick(List<Strategy> strategies, BigDecimal balance, long daysOverdue) {
        return strategies.stream()
                .sorted(Comparator.comparingInt(Strategy::priority).thenComparing(Strategy::name))
                .filter(s -> matches(s, balance, daysOverdue))
                .findFirst().orElse(null);
    }

    /** Why a case should be left alone right now, or null when strategy steps may be raised. */
    public static String skipReason(String caseStatus, boolean hasOpenPromise, Long daysSinceContact, int quietDays) {
        if (!ACTIVE_STATUSES.contains(caseStatus)) return "The case is " + caseStatus.toLowerCase().replace('_', ' ') + ".";
        if (hasOpenPromise) return "A promise to pay is open.";
        if (daysSinceContact != null && daysSinceContact < quietDays) return "The debtor was contacted " + daysSinceContact + " day(s) ago.";
        return null;
    }

    /**
     * Steps whose day has come and that were not yet handled. Only the latest is raised; earlier ones are recorded as
     * skipped so a case that joins late does not get a pile of overlapping tasks.
     */
    public static Decision decide(List<Step> steps, long daysOverdue, Set<UUID> handled) {
        List<Step> due = new ArrayList<>();
        for (Step s : steps) if (s.afterDays() <= daysOverdue && !handled.contains(s.id())) due.add(s);
        due.sort(Comparator.comparingInt(Step::afterDays));
        if (due.isEmpty()) return new Decision(null, List.of());
        return new Decision(due.get(due.size() - 1), due.subList(0, due.size() - 1));
    }

    public static String title(Step step, String caseNumber, String debtor, long daysOverdue) {
        String what = switch (step.action()) {
            case "CALL" -> "Call";
            case "SEND_REMINDER" -> "Send reminder";
            case "SEND_DEMAND" -> "Send formal demand";
            case "LEGAL_REVIEW" -> "Review for legal action";
            case "REVIEW_WRITE_OFF" -> "Review for write-off";
            default -> "Follow up";
        };
        String t = what + ": " + caseNumber + ", " + debtor + " (" + daysOverdue + " days overdue)";
        if (step.instruction() != null && !step.instruction().isBlank()) t += ". " + step.instruction().trim();
        return t.length() > 480 ? t.substring(0, 480) : t;
    }

    public static boolean isLegal(Step step) { return "LEGAL_REVIEW".equals(step.action()); }
}
