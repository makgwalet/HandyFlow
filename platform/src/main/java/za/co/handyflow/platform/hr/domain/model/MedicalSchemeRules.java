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
