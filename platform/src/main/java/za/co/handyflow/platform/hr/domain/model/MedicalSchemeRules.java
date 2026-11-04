package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Who is covered by a medical scheme on a given day, and what may be recorded. Pure, so it runs without a database.
 * <p>
 * The medical scheme fees tax credit comes from the people covered in the pay period, not from the employee's number
 * of children or household members.
 */
public final class MedicalSchemeRules {

    public static final int MAX_DEPENDANTS = 20;

    private MedicalSchemeRules() {}

    /** Who is covered besides the employee. {@code total} is what the tax credit counts. */
    public record Dependants(int spouses, int children, int other) {
        public int total() { return spouses + children + other; }
    }

    /**
     * Works out who is covered from what was sent: either the three counts, or only a total (older callers), which is
     * then recorded as "other" because the split is not known.
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static Dependants dependantsOf(Integer total, Integer spouses, Integer children, Integer other) {
        boolean split = spouses != null || children != null || other != null;
        if (!split) {
            if (total == null) throw new IllegalArgumentException("Say how many dependants are covered, 0 if none.");
            if (total < 0 || total > MAX_DEPENDANTS) throw new IllegalArgumentException("Dependants must be from 0 to " + MAX_DEPENDANTS + ".");
            return new Dependants(0, 0, total);
        }
        int s = spouses == null ? 0 : spouses, c = children == null ? 0 : children, o = other == null ? 0 : other;
        if (s < 0 || s > 1) throw new IllegalArgumentException("A member can cover one spouse or partner, or none.");
        if (c < 0 || c > MAX_DEPENDANTS || o < 0 || o > MAX_DEPENDANTS) throw new IllegalArgumentException("Children and other dependants must each be from 0 to " + MAX_DEPENDANTS + ".");
        Dependants d = new Dependants(s, c, o);
        if (d.total() > MAX_DEPENDANTS) throw new IllegalArgumentException("At most " + MAX_DEPENDANTS + " dependants can be covered in all.");
        if (total != null && total != d.total()) throw new IllegalArgumentException("The dependants do not add up: " + s + " + " + c + " + " + o + " is " + d.total() + ", not " + total + ".");
        return d;
    }

    /** A contribution is an amount of 0 or more, or not given. */
    public static java.math.BigDecimal contribution(java.math.BigDecimal amount, String who) {
        if (amount == null) return null;
        if (amount.signum() < 0) throw new IllegalArgumentException("The " + who + " contribution cannot be negative.");
        if (amount.scale() > 2) throw new IllegalArgumentException("The " + who + " contribution can have at most two decimals.");
        return amount;
    }

    /** One stretch of membership. {@code to} is null while it is still running. */
    public record Cover(LocalDate from, LocalDate to, int dependants) {
        boolean includes(LocalDate day) {
            return !day.isBefore(from) && (to == null || !day.isAfter(to));
        }
    }

    /**
     * @param count   people covered, the employee included; 0 means no credit
     * @param assumed true when no membership is on record and the count is a guess from the employee's contribution,
     *                so the answer needs a person to confirm it
     */
    public record Members(int count, boolean assumed) {}

    /**
     * The people covered on {@code day}. With a membership on record the answer is exact. Without one, an employee who
     * pays a medical aid contribution is taken as covering themselves only, and flagged as assumed. Otherwise nobody.
     */
    public static Members membersOn(List<Cover> covers, LocalDate day, BigDecimal medicalAidContribution) {
        for (Cover c : covers) if (c.includes(day)) return new Members(1 + c.dependants(), false);
        boolean pays = medicalAidContribution != null && medicalAidContribution.signum() > 0;
        return pays ? new Members(1, true) : new Members(0, false);
    }

    /**
     * Checks a new membership against the ones already recorded and says which open one to close, if any.
     *
     * @return the start date of the open membership that must end the day before {@code from}, or null if none
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static LocalDate checkNew(List<Cover> existing, LocalDate from, int dependants) {
        if (from == null) throw new IllegalArgumentException("The start date is required.");
        if (dependants < 0 || dependants > MAX_DEPENDANTS)
            throw new IllegalArgumentException("Dependants must be from 0 to " + MAX_DEPENDANTS + ".");
        Cover latest = existing.stream().max(Comparator.comparing(Cover::from)).orElse(null);
        if (latest == null) return null;
        if (!from.isAfter(latest.from()))
            throw new IllegalArgumentException("A membership already starts on " + latest.from() + ". The new one must start after it.");
        if (latest.to() != null && !from.isAfter(latest.to()))
            throw new IllegalArgumentException("That period overlaps a membership that ends on " + latest.to() + ".");
        return latest.to() == null ? latest.from() : null;
    }
}
