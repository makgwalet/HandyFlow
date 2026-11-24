package za.co.handyflow.platform.contracting.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of the approval escalation engine, with no database or Spring in them so they can be tested directly.
 *
 * <p>The engine only ever reminds, escalates and records. It does not decide an approval: nothing here approves, rejects or uses one up.
 */
public final class EscalationRules {

    private EscalationRules() {}

    /** How far an approval has got. The level only goes up, and each level is sent once. */
    public enum Stage {
        NONE, REMINDER_1, REMINDER_2, ESCALATED;

        public int level() { return ordinal(); }

        public static Stage ofLevel(int level) {
            if (level < 0 || level >= values().length) throw new IllegalArgumentException("Unknown escalation level " + level);
            return values()[level];
        }
    }

    /** Hours of waiting (on the company's clock) at which each step happens. */
    public record Thresholds(int reminder1Hours, int reminder2Hours, int escalateHours) {
        public static final int MAX_HOURS = 720;

        public Thresholds {
            if (reminder1Hours < 1) throw new IllegalArgumentException("The first reminder must be at least 1 hour");
            if (reminder2Hours <= reminder1Hours) throw new IllegalArgumentException("The second reminder must come after the first");
            if (escalateHours <= reminder2Hours) throw new IllegalArgumentException("Escalation must come after the second reminder");
            if (escalateHours > MAX_HOURS) throw new IllegalArgumentException("Escalation cannot be later than " + MAX_HOURS + " hours");
        }

        public static Thresholds defaults() { return new Thresholds(24, 48, 72); }

        public int hoursFor(Stage s) {
            return switch (s) {
                case NONE -> 0;
                case REMINDER_1 -> reminder1Hours;
                case REMINDER_2 -> reminder2Hours;
                case ESCALATED -> escalateHours;
            };
        }
    }

    /** The furthest stage that waiting this long has reached. */
    public static Stage stageReached(long waitedMinutes, Thresholds t) {
        Stage reached = Stage.NONE;
        for (Stage s : new Stage[]{Stage.REMINDER_1, Stage.REMINDER_2, Stage.ESCALATED})
            if (waitedMinutes >= t.hoursFor(s) * 60L) reached = s;
        return reached;
    }

    /**
     * The step to take now, or empty when nothing is due. When several are overdue at once (the engine was down, or a long closure) only the
     * furthest one is sent, not a burst of every missed step.
     */
    public static Optional<Stage> stageToSend(int currentLevel, long waitedMinutes, Thresholds t) {
        Stage reached = stageReached(waitedMinutes, t);
        return reached.level() > currentLevel ? Optional.of(reached) : Optional.empty();
    }

    // ── who is told ──────────────────────────────────────────────────────────

    /** A person who cannot approve from one date to another (both days included), and who covers for them, if anyone. */
    public record Absence(UUID userId, LocalDate from, LocalDate to, UUID delegateId) {
        public boolean covers(UUID user, LocalDate day) {
            return userId.equals(user) && !day.isBefore(from) && !day.isAfter(to);
        }
    }

    private static final int MAX_DELEGATION_HOPS = 3;

    /**
     * The user who should be told in place of {@code user} today: the user themselves when they are in, their delegate when they are out
     * (and the delegate is in), nobody when no one can cover. Follows a delegate who is also out, up to three steps, and never loops.
     */
    public static Optional<UUID> presentOrCover(UUID user, List<Absence> absences, LocalDate today) {
        UUID current = user;
        Set<UUID> seen = new LinkedHashSet<>();
        for (int hop = 0; hop <= MAX_DELEGATION_HOPS; hop++) {
            if (!seen.add(current)) return Optional.empty();
            Absence out = firstAbsence(current, absences, today);
            if (out == null) return Optional.of(current);
            if (out.delegateId() == null) return Optional.empty();
            current = out.delegateId();
        }
        return Optional.empty();
    }

    private static Absence firstAbsence(UUID user, List<Absence> absences, LocalDate today) {
        for (Absence a : absences) if (a.covers(user, today)) return a;
        return null;
    }

    /** Who a reminder goes to. When no one can be reached {@code fallbackToEscalationContacts} is set, so a reminder is never silently dropped. */
    public record Routing(List<UUID> people, boolean fallbackToEscalationContacts) {}

    /**
     * Reminder recipients. An assigned approver is told first; if they are out with no cover, or there is no assignee, everyone able to
     * approve is told. The person who asked for the approval is never one of them, and people who are out are replaced by their cover.
     */
    public static Routing routeReminder(UUID assignedTo, Collection<UUID> approverPool, UUID requester, List<Absence> absences, LocalDate today) {
        if (assignedTo != null) {
            List<UUID> one = resolve(List.of(assignedTo), requester, absences, today);
            if (!one.isEmpty()) return new Routing(one, false);
        }
        List<UUID> all = resolve(approverPool, requester, absences, today);
        return new Routing(all, all.isEmpty());
    }

    /**
     * Escalation recipients: the named contacts, or the administrators when none are named. Out-of-office people are replaced by their cover.
     * The requester is left out (they are told separately that it was escalated).
     */
    public static List<UUID> routeEscalation(Collection<UUID> namedContacts, Collection<UUID> administrators, UUID requester, List<Absence> absences, LocalDate today) {
        Collection<UUID> base = namedContacts == null || namedContacts.isEmpty() ? administrators : namedContacts;
        return resolve(base, requester, absences, today);
    }

    private static List<UUID> resolve(Collection<UUID> people, UUID requester, List<Absence> absences, LocalDate today) {
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (people == null) return List.of();
        for (UUID p : people) {
            if (p == null || p.equals(requester)) continue;
            presentOrCover(p, absences, today).filter(u -> !u.equals(requester)).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    // ── stored text forms ────────────────────────────────────────────────────

    public static List<UUID> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try { out.add(UUID.fromString(s)); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Not a valid user id: " + s); }
        }
        return new ArrayList<>(out);
    }

    public static String joinIds(Collection<UUID> ids) {
        if (ids == null) return "";
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        return String.join(",", unique.stream().map(UUID::toString).toList());
    }

    private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.values());

    public static Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Choose at least one working day");
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String part : csv.split(",")) {
            String s = part.trim().toUpperCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            DayOfWeek match = null;
            for (DayOfWeek d : WEEK) if (d.name().equals(s) || d.name().substring(0, 3).equals(s)) match = d;
            if (match == null) throw new IllegalArgumentException("Not a day of the week: " + part.trim());
            out.add(match);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Choose at least one working day");
        return out;
    }

    /** Stored form: three-letter codes in week order, for example {@code MON,TUE,WED}. */
    public static String joinDays(Collection<DayOfWeek> days) {
        return String.join(",", WEEK.stream().filter(days::contains).map(d -> d.name().substring(0, 3)).toList());
    }
}
