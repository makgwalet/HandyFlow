package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/** When a client is sent an SMS about a deadline, and what it says. Pure rules; nothing here sends anything. */
public final class SmsReminderRules {

    public static final int MAX_LENGTH = 300;
    public static final int MIN_NOTE = 10;
    public static final String WEEK_AHEAD = "WEEK_AHEAD";
    public static final String DAY_BEFORE = "DAY_BEFORE";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SmsReminderRules() {}

    /** Spaces and dashes are tidied away; the number must then be in international form. Never guesses a country. */
    public static String normalisePhone(String raw) {
        if (raw == null) throw new IllegalArgumentException("A cell number is needed");
        String p = raw.replaceAll("[\\s\\-()]", "");
        if (!E164.matcher(p).matches()) throw new IllegalArgumentException("Give the number in international form, for example +27821234567");
        return p;
    }

    public static String requireNote(String note, String what) {
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException(what + " needs at least " + MIN_NOTE + " characters");
        return note.trim();
    }

    /** Which reminder, if any, is due today. A missed run catches up, because the log stops a repeat. Overdue deadlines get nothing here. */
    public static String kindFor(LocalDate dueDate, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, dueDate);
        if (days < 0 || days > 7) return null;
        return days <= 1 ? DAY_BEFORE : WEEK_AHEAD;
    }

    public static String message(String description, LocalDate dueDate, LocalDate today) {
        String kind = kindFor(dueDate, today);
        if (kind == null) throw new IllegalArgumentException("Nothing is due to be sent for this date");
        String lead = DAY_BEFORE.equals(kind) ? (dueDate.equals(today) ? "Due today: " : "Due tomorrow: ") : "Reminder: ";
        String tail = " (" + dueDate.format(DATE) + "). Questions? Contact your adviser. To stop these SMS reminders, tell your adviser.";
        String what = description == null || description.isBlank() ? "a compliance deadline" : description.trim();
        int room = MAX_LENGTH - lead.length() - tail.length();
        if (what.length() > room) what = what.substring(0, Math.max(0, room - 1)).trim() + "…";
        return lead + what + tail;
    }

    public static String outcomeText(String outcome) {
        return "HANDED_OVER".equals(outcome) ? "Handed to the SMS provider (delivery is not confirmed)" : "Could not be handed over";
    }
}
