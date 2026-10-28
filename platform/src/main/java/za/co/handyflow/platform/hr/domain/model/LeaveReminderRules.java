package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * When, and in what words, employees are told that annual leave will be lost at the coming rollover. Pure: no loading, no sending.
 * <p>
 * The reminders go out on 1 October, 1 November, 1 December and 15 December, and only when the company has a carry-over limit that applies to
 * the coming rollover. The words say how many days, the limit, and that leave can be booked; they never name anyone else.
 */
public final class LeaveReminderRules {

    private LeaveReminderRules() {}

    public static boolean reminderToday(LocalDate today) {
        int m = today.getMonthValue(), d = today.getDayOfMonth();
        return (d == 1 && (m == 10 || m == 11 || m == 12)) || (m == 12 && d == 15);
    }

    public static String days(BigDecimal d) {
        BigDecimal v = d.stripTrailingZeros();
        String s = (v.scale() < 0 ? v.setScale(0) : v).toPlainString();
        return s + (s.equals("1") ? " day" : " days");
    }

    public static String employeeTitle(BigDecimal wouldLose) {
        return "You may lose " + days(wouldLose) + " of annual leave at year end";
    }

    public static String employeeMessage(BigDecimal wouldLose, BigDecimal cap, BigDecimal availableNow, int year) {
        return "Company policy carries over at most " + days(cap) + " of annual leave into " + (year + 1) + ". You have " + days(availableNow)
                + " now, and if you take no more leave before 31 December " + year + " you would lose " + days(wouldLose)
                + ". Book the leave you want to use while there is time.";
    }

    public static String adminTitle(int people) {
        return people + (people == 1 ? " employee" : " employees") + " could lose annual leave at year end";
    }

    public static String adminMessage(int people, BigDecimal cap, int year) {
        return people + (people == 1 ? " employee has" : " employees have") + " more annual leave than the " + days(cap)
                + " that can be carried into " + (year + 1) + ". Each has been told. See the Leave tab for the list.";
    }

    // ── Carried-over leave that is about to expire ──────────────────────────────────────────────

    /** Days before the last day carried leave can be used on which the people concerned are told. */
    public static final Set<Long> EXPIRY_WARN_DAYS = Set.of(30L, 14L, 7L, 1L);

    public static long daysLeft(LocalDate expiryDate, LocalDate today) { return ChronoUnit.DAYS.between(today, expiryDate); }

    public static boolean expiryReminderToday(LocalDate expiryDate, LocalDate today) {
        return expiryDate != null && EXPIRY_WARN_DAYS.contains(daysLeft(expiryDate, today));
    }

    public static String expiryEmployeeTitle(BigDecimal unused) {
        return days(unused) + " of your carried-over leave will expire";
    }

    public static String expiryEmployeeMessage(BigDecimal unused, LocalDate expiryDate, LocalDate today, int year) {
        long left = daysLeft(expiryDate, today);
        return "You still have " + days(unused) + " of the annual leave carried into " + year + ". Under company policy it is forfeited after "
                + expiryDate + " (" + left + (left == 1 ? " day" : " days") + " from now) if you do not use it. Book it while there is time.";
    }

    // ── The manager ─────────────────────────────────────────────────────────────────────────────

    /** One person in a manager's team and the days involved. */
    public record Line(String name, BigDecimal days) {}

    public static final int MAX_LISTED = 10;

    public static String teamTitle(int people, boolean expiry) {
        return people + (people == 1 ? " person in your team " : " people in your team ")
                + (expiry ? "will lose carried-over leave" : "could lose annual leave at year end");
    }

    /** Names and days for the first {@link #MAX_LISTED} (the caller sorts them), then how many more. */
    public static String teamList(List<Line> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size() && i < MAX_LISTED; i++) {
            if (i > 0) b.append(", ");
            b.append(lines.get(i).name()).append(" (").append(days(lines.get(i).days())).append(")");
        }
        if (lines.size() > MAX_LISTED) b.append(" and ").append(lines.size() - MAX_LISTED).append(" more");
        return b.toString();
    }

    public static String teamExpiryMessage(List<Line> lines, LocalDate expiryDate) {
        return "Carried-over leave not used by " + expiryDate + " is forfeited under company policy: " + teamList(lines)
                + ". Encourage them to book it while there is time.";
    }

    public static String teamCapMessage(List<Line> lines, BigDecimal cap, int year) {
        return "Company policy carries over at most " + days(cap) + " of annual leave into " + (year + 1) + ". If they take no more leave before 31 December "
                + year + " these people would lose: " + teamList(lines) + ". Encourage them to book it while there is time.";
    }
}
