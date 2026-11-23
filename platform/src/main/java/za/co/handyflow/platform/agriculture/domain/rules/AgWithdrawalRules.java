package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Withdrawal periods (animals) and pre-harvest intervals (crops): the days that must pass after a treatment before the animal
 * may be sold or slaughtered, or the crop harvested. Before this they were typed in and never used.
 * <p>
 * A treatment on 1 Dec with a 14 day period is clear from 15 Dec ({@code date + days}); on 14 Dec it is still held. The same
 * rule is used for both animals and crops. A treatment that is still only scheduled has not happened yet and holds nothing.
 * A hold can be overridden, but only with a written reason, and the reason is kept on the record it was used for.
 */
public final class AgWithdrawalRules {

    /** Longest period accepted (two years). Anything longer is almost certainly a typing slip. */
    public static final int MAX_DAYS = 730;
    public static final int MIN_REASON = 3;
    /** Animal statuses that send meat to market: these are held while a withdrawal period runs. A death or a return to active is not. */
    public static final java.util.Set<String> LEAVES_FOR_MARKET = java.util.Set.of("SOLD", "CULLED", "TRANSFERRED_OUT");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private AgWithdrawalRules() {}

    /** One treatment or spray: when, for how many days, with what. {@code scheduled} treatments are ignored. */
    public record Treatment(LocalDate date, Integer days, String product, boolean scheduled) {}

    /** The treatment that is holding things up, and the first day it is clear. */
    public record Hold(LocalDate treatedOn, LocalDate clearOn, String product) {}

    /** What a milk or egg period holds back. */
    public enum Produce {
        MILK("milk"), EGGS("eggs");
        private final String label;
        Produce(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** The days this treatment holds back {@code produce}: the milk period for milk, the egg period for eggs. */
    public static Integer produceDays(Produce produce, Integer milkDays, Integer eggDays) {
        return produce == Produce.MILK ? milkDays : eggDays;
    }

    /** The first day clear of this treatment; null when no period was given. */
    public static LocalDate clearOn(LocalDate date, Integer days) {
        if (date == null || days == null || days <= 0) return null;
        return date.plusDays(days);
    }

    /** A period, if given, must be 0 to {@value #MAX_DAYS} days. */
    public static void requireValidDays(Integer days, String what) {
        if (days != null && (days < 0 || days > MAX_DAYS))
            throw new IllegalArgumentException(what + " must be between 0 and " + MAX_DAYS + " days");
    }

    /** The treatment that keeps the latest hold on {@code asOf}, or empty when everything is clear on that day. */
    public static Optional<Hold> activeHold(List<Treatment> treatments, LocalDate asOf) {
        if (treatments == null || asOf == null) return Optional.empty();
        return treatments.stream()
                .filter(t -> t != null && !t.scheduled() && t.date() != null && !t.date().isAfter(asOf))
                .filter(t -> clearOn(t.date(), t.days()) != null)
                .map(t -> new Hold(t.date(), clearOn(t.date(), t.days()), t.product()))
                .filter(h -> asOf.isBefore(h.clearOn()))
                .max(Comparator.comparing(Hold::clearOn));
    }

    /**
     * Passes when nothing holds, or when a reason is given. Returns the line to keep on the record when a hold was overridden,
     * otherwise null. Throws (a 409) when held and no reason is given.
     *
     * @param subject "Bessie (tag AN-001)"
     * @param cause   "withdrawal period" or "pre-harvest interval"
     * @param action  "sold or slaughtered" or "harvested"
     */
    public static String requireClear(Optional<Hold> hold, String subject, String cause, String action, String overrideReason) {
        if (hold.isEmpty()) return null;
        Hold h = hold.get();
        String reason = overrideReason == null ? "" : overrideReason.trim();
        if (reason.length() < MIN_REASON) {
            throw new IllegalStateException(capitalise(subject) + " is within the " + cause + " after "
                    + (h.product() == null || h.product().isBlank() ? "a treatment" : h.product().trim())
                    + " on " + DAY.format(h.treatedOn()) + " and cannot be " + action + " before " + DAY.format(h.clearOn())
                    + ". If it is safe to go anyway, give a reason to override.");
        }
        return "Override of " + cause + " (held until " + DAY.format(h.clearOn()) + "): " + reason;
    }

    /** What was said to go ahead during a hold, kept as its own field on the record: the reason, and the day the hold would have run to. */
    public record Override(String reason, LocalDate heldUntil) {}

    /**
     * Like {@link #requireClear}, but returns the override as data (reason and held-until day) instead of a line of text, so it
     * can be stored in its own columns. Empty when nothing was held; throws (a 409) when held and no reason is given.
     */
    public static Optional<Override> requireClearOrOverride(Optional<Hold> hold, String subject, String cause, String action, String overrideReason) {
        if (hold.isEmpty()) return Optional.empty();
        requireClear(hold, subject, cause, action, overrideReason);   // throws when the reason is missing or too short
        return Optional.of(new Override(overrideReason.trim(), hold.get().clearOn()));
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The record's notes with the override line added, so the reason is kept where the sale or harvest is. */
    public static String withOverrideNote(String notes, String overrideLine) {
        if (overrideLine == null) return notes;
        return notes == null || notes.isBlank() ? overrideLine : notes.trim() + "\n" + overrideLine;
    }
}
