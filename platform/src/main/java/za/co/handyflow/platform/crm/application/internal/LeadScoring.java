package za.co.handyflow.platform.crm.application.internal;

import java.util.ArrayList;
import java.util.List;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Facts;

/** Adds up the points of every rule a lead matches. Pure. */
final class LeadScoring {
    static final int HOT_FROM = 60, WARM_FROM = 30;

    private LeadScoring() {}

    record Rule(String name, Condition condition, int points) {}

    record Result(int score, String band, List<String> reasons) {}

    static String band(int score) { return score >= HOT_FROM ? "HOT" : score >= WARM_FROM ? "WARM" : "COLD"; }

    /** The score is the sum of matching points, kept between 0 and 100. */
    static Result score(List<Rule> rules, Facts facts) {
        int sum = 0;
        List<String> why = new ArrayList<>();
        for (Rule r : rules) {
            if (!LeadConditions.matches(r.condition(), facts)) continue;
            sum += r.points();
            why.add(r.name() + " (" + (r.points() > 0 ? "+" : "") + r.points() + ")");
        }
        int score = Math.max(0, Math.min(100, sum));
        return new Result(score, band(score), why);
    }

    static void checkRule(String name, Condition condition, int points) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the scoring rule");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        LeadConditions.check(condition);
        if (points == 0 || points < -100 || points > 100) throw new IllegalArgumentException("Points must be between -100 and 100, and not zero");
    }
}
