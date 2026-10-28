package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
