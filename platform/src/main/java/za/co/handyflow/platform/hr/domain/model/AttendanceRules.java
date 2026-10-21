package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What a day or a week of attendance means: worked time, late arrival, absence, and the overtime that follows from it.
 * <p>
 * Pure: no database and no clock, so the same punches and the same schedule always give the same answer. Times are kept as
 * instants and read in South African time.
 * <p>
 * Assumptions, stated so they can be changed: a schedule is the employee's ordinary hours (no shifts across midnight); work
 * beyond the scheduled hours on a scheduled day, and any work on an unscheduled day, is overtime; one punch pair in a day has
 * its unpaid break taken off when it is longer than the meal-interval trigger, while several pairs are taken to have the
 * breaks between them; work on a public holiday is recorded but its pay is not calculated here.
 */
public final class AttendanceRules {

    private AttendanceRules() {}

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    public static final int LATE_GRACE_MINUTES = 5;
    public static final int MAX_ENTRY_HOURS = 16;

    // ── Schedules ───────────────────────────────────────────────────────────────────────────────

    public record Schedule(Set<DayOfWeek> days, LocalTime start, LocalTime end, int breakMinutes) {
        /** Ordinary minutes worked on a scheduled day. */
        public int dailyMinutes() { return (int) Duration.between(start, end).toMinutes() - breakMinutes; }
        public int weeklyMinutes() { return dailyMinutes() * days.size(); }
        public boolean works(DayOfWeek d) { return days.contains(d); }
    }

    /** "MON,TUE" to a set; unknown words are ignored (the problem check catches an empty result). */
    public static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        if (csv == null) return out;
        for (String p : csv.split(",")) {
            String w = p.trim().toUpperCase();
            for (DayOfWeek d : DayOfWeek.values()) if (d.name().startsWith(w) && w.length() >= 3) out.add(d);
        }
        return out;
    }

    public static String daysCsv(Set<DayOfWeek> days) {
        List<String> l = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) if (days.contains(d)) l.add(d.name().substring(0, 3));
        return String.join(",", l);
    }

    public static String scheduleProblem(String name, Set<DayOfWeek> days, LocalTime start, LocalTime end, int breakMinutes) {
        if (name == null || name.isBlank()) return "Give the schedule a name.";
        if (name.trim().length() > 100) return "Keep the name under 100 characters.";
        if (days == null || days.isEmpty()) return "Choose at least one working day.";
        if (start == null || end == null) return "Enter the start and end times.";
        if (!end.isAfter(start)) return "The end time must be after the start time. Shifts across midnight are not supported yet.";
        if (breakMinutes < 0 || breakMinutes > 180) return "The unpaid break must be between 0 and 180 minutes.";
        Schedule s = new Schedule(days, start, end, breakMinutes);
        if (s.dailyMinutes() <= 0) return "The break takes up the whole day.";
        if (s.dailyMinutes() > 12 * 60) return "A working day of more than 12 hours is not allowed.";
        return null;
    }

    /** Things worth knowing that do not stop a schedule being saved. */
    public static List<String> scheduleWarnings(Schedule s, WorkingTimeRules.Values rules) {
        List<String> out = new ArrayList<>();
        if (s.weeklyMinutes() > rules.weeklyOrdinaryMax() * 60)
            out.add("This is " + hours(s.weeklyMinutes()) + " ordinary hours a week; the Act allows " + rules.weeklyOrdinaryMax() + ".");
        int dayLimit = s.days().size() > 5 ? 8 : 9;
        if (s.dailyMinutes() > dayLimit * 60)
            out.add("This is " + hours(s.dailyMinutes()) + " ordinary hours a day; the Act allows " + dayLimit + " on a " + (s.days().size() > 5 ? "six" : "five") + "-day week or fewer.");
        return out;
    }

    // ── Punches ─────────────────────────────────────────────────────────────────────────────────

    public static LocalDate workDate(Instant in) { return in.atZone(ZONE).toLocalDate(); }

    public static String entryProblem(Instant in, Instant out, Instant now) {
        if (in == null) return "Enter the time in.";
        if (in.isAfter(now.plusSeconds(300))) return "The time in is in the future.";
        if (out == null) return null;
        if (!out.isAfter(in)) return "The time out must be after the time in.";
        if (out.isAfter(now.plusSeconds(300))) return "The time out is in the future.";
        if (Duration.between(in, out).toHours() >= MAX_ENTRY_HOURS) return "That is " + MAX_ENTRY_HOURS + " hours or more in one go. Check the dates.";
        return null;
    }

    public record Punch(Instant in, Instant out) {}

    /** Minutes worked on a day from its punches; open punches (no out) count for nothing until they are closed. */
    public static int workedMinutes(List<Punch> punches, int breakMinutes, WorkingTimeRules.Values rules) {
        List<Punch> closed = punches.stream().filter(p -> p.out() != null).toList();
        if (closed.isEmpty()) return 0;
        int total = 0;
        for (Punch p : closed) total += (int) Duration.between(p.in(), p.out()).toMinutes();
        if (closed.size() == 1 && total > rules.mealAfterHours() * 60) total = Math.max(0, total - breakMinutes);
        return total;
    }

    // ── A day ───────────────────────────────────────────────────────────────────────────────────

    public static final String PRESENT = "PRESENT", LATE = "LATE", ABSENT = "ABSENT", ON_LEAVE = "ON_LEAVE", PUBLIC_HOLIDAY = "PUBLIC_HOLIDAY",
            REST_DAY = "REST_DAY", INCOMPLETE = "INCOMPLETE", PENDING = "PENDING";

    /** How overtime is paid: 1.5 times, 2 times (a Sunday not ordinarily worked), or not worked out here. */
    public static final String OT_STANDARD = "OVERTIME", OT_SUNDAY = "SUNDAY", OT_HOLIDAY = "PUBLIC_HOLIDAY";

    public record Day(LocalDate date, String status, int expectedMinutes, int workedMinutes, int lateMinutes, int shortMinutes,
                      String overtimeKind, int overtimeMinutes, List<String> flags) {}

    /**
     * @param firstIn   the first time in of the day, or null when there is none
     * @param anyOpen   a punch has no time out
     * @param today     today's date in South African time, so a day not yet over is not called absence
     */
    public static Day day(LocalDate date, Schedule schedule, boolean holiday, boolean onLeave, int worked, Instant firstIn, boolean anyOpen,
                          LocalDate today, WorkingTimeRules.Values rules) {
        boolean scheduled = schedule.works(date.getDayOfWeek());
        int expected = scheduled && !holiday && !onLeave ? schedule.dailyMinutes() : 0;
        List<String> flags = new ArrayList<>();
        if (worked > rules.dailyMax() * 60) flags.add("More than " + rules.dailyMax() + " hours in one day (s 10).");

        String kind = null;
        int overtime = 0;
        if (worked > 0) {
            if (holiday) { kind = OT_HOLIDAY; overtime = worked; flags.add("Worked on a public holiday: the pay due under s 18 is not worked out here."); }
            else if (!scheduled || onLeave) {
                overtime = worked;
                kind = date.getDayOfWeek() == DayOfWeek.SUNDAY ? OT_SUNDAY : OT_STANDARD;
                if (onLeave) flags.add("Worked on a day of approved leave. Check the leave.");
            } else if (worked > expected) {
                overtime = worked - expected;
                kind = OT_STANDARD;
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY) flags.add("Sunday is an ordinary working day here: the Sunday rate for ordinary hours (s 16) is not worked out.");
            }
        }

        String status;
        int late = 0, shortBy = 0;
        if (holiday && worked == 0) status = PUBLIC_HOLIDAY;
        else if (onLeave && worked == 0) status = ON_LEAVE;
        else if (!scheduled && worked == 0) status = REST_DAY;
        else if (anyOpen && date.isBefore(today)) { status = INCOMPLETE; flags.add("Clocked in but never out."); }
        else if (worked == 0 && firstIn == null) status = date.isBefore(today) ? ABSENT : PENDING;
        else {
            status = PRESENT;
            if (scheduled && !holiday && !onLeave && firstIn != null) {
                late = (int) Math.max(0, Duration.between(date.atTime(schedule.start()).atZone(ZONE).toInstant(), firstIn).toMinutes());
                if (late > LATE_GRACE_MINUTES) status = LATE; else late = 0;
                if (!anyOpen && worked < expected) shortBy = expected - worked;
            }
        }
        return new Day(date, status, expected, worked, late, shortBy, kind, overtime, flags);
    }

    // ── A week ──────────────────────────────────────────────────────────────────────────────────

    /** Flags for the Monday-to-Sunday week the days fall in: ordinary hours, overtime hours. */
    public static List<String> weekFlags(List<Day> weekDays, WorkingTimeRules.Values rules) {
        List<String> out = new ArrayList<>();
        int ordinary = 0, overtime = 0;
        for (Day d : weekDays) {
            if (OT_HOLIDAY.equals(d.overtimeKind())) continue; // holiday work is neither: it is paid under s 18
            ordinary += d.workedMinutes() - d.overtimeMinutes();
            overtime += d.overtimeMinutes();
        }
        if (overtime > rules.weeklyOvertimeMax() * 60) out.add("Overtime of " + hours(overtime) + " hours is above the " + rules.weeklyOvertimeMax() + " hours a week the Act allows (s 10).");
        if (ordinary > rules.weeklyOrdinaryMax() * 60) out.add("Ordinary hours of " + hours(ordinary) + " are above the " + rules.weeklyOrdinaryMax() + " a week (s 9).");
        return out;
    }

    /** Less than the daily rest between the end of one day's work and the start of the next (s 15). Null when fine. */
    public static String restProblem(Instant previousOut, Instant nextIn, WorkingTimeRules.Values rules) {
        if (previousOut == null || nextIn == null) return null;
        long h = Duration.between(previousOut, nextIn).toMinutes();
        return h < rules.dailyRestHours() * 60L ? "Only " + hours((int) h) + " hours of rest before the next day's work; the Act requires " + rules.dailyRestHours() + " (s 15)." : null;
    }

    // ── Money ───────────────────────────────────────────────────────────────────────────────────

    /** Hourly rate for a monthly salary: monthly x 12 / 52 / ordinary weekly hours (s 35(3)). Four decimals, so rounding happens once. */
    public static BigDecimal hourlyRate(BigDecimal monthlyGross, int weeklyOrdinaryMinutes) {
        if (monthlyGross == null || monthlyGross.signum() <= 0 || weeklyOrdinaryMinutes <= 0) return BigDecimal.ZERO;
        BigDecimal weeklyHours = BigDecimal.valueOf(weeklyOrdinaryMinutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        return monthlyGross.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(52), 6, RoundingMode.HALF_UP).divide(weeklyHours, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal multiplier(String kind, WorkingTimeRules.Values rules) {
        return OT_SUNDAY.equals(kind) ? rules.sundayMultiplier() : rules.overtimeMultiplier();
    }

    /** Pay for overtime: hourly rate x hours x multiplier, to the cent. */
    public static BigDecimal overtimePay(BigDecimal hourly, int minutes, BigDecimal multiplier) {
        return hourly.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP)
                .multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public static String hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
