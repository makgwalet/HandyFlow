package za.co.handyflow.platform.hr.domain.model;

import java.time.Duration;
import java.time.Instant;

/** What may be kept as an employee draft. Pure, so it runs without a database. */
public final class EmployeeDraftRules {

    public static final int MAX_PAYLOAD_CHARS = 60_000;
    public static final int MAX_DRAFTS_PER_PERSON = 10;
    public static final Duration KEEP_FOR = Duration.ofDays(30);

    private EmployeeDraftRules() {}

    /** @throws IllegalArgumentException with a message fit to show the user */
    public static void check(String payload, int step) {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("There is nothing to save in this draft.");
        if (payload.length() > MAX_PAYLOAD_CHARS) throw new IllegalArgumentException("This draft is too large to save.");
        String p = payload.stripLeading();
        if (!p.startsWith("{")) throw new IllegalArgumentException("The draft is not in the form the page sends.");
        if (step < 0 || step > 5) throw new IllegalArgumentException("The step must be from 0 to 5.");
    }

    /** A short name for the list: the person's name when the draft has one. */
    public static String label(String firstName, String lastName) {
        String n = ((firstName == null ? "" : firstName.strip()) + " " + (lastName == null ? "" : lastName.strip())).strip();
        if (n.isEmpty()) return "Unnamed employee";
        return n.length() > 200 ? n.substring(0, 200) : n;
    }

    public static boolean expired(Instant updatedAt, Instant now) {
        return updatedAt.plus(KEEP_FOR).isBefore(now);
    }
}
