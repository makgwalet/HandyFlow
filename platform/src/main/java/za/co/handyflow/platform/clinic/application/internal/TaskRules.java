package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.util.Set;

/** Rules for clinic tasks (patch 0151). Pure, so they can be tested without a database. */
final class TaskRules {

    private TaskRules() {}

    static final Set<String> KINDS = Set.of("GENERAL", "RESULT_FOLLOW_UP", "CALL_PATIENT", "RECALL");

    static String kind(String raw) {
        String k = raw == null || raw.isBlank() ? "GENERAL" : raw.trim().toUpperCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Unknown task type: " + raw);
        return k;
    }

    /** 3 to 200 characters once trimmed. */
    static String title(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < 3) throw new IllegalArgumentException("Give the task a title of at least 3 characters");
        if (t.length() > 200) throw new IllegalArgumentException("Keep the title under 200 characters");
        return t;
    }

    /** Optional, up to 1000 characters. */
    static String detail(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.length() > 1000) throw new IllegalArgumentException("Keep the detail under 1000 characters");
        return t;
    }

    /** A new task cannot be due before today. */
    static LocalDate due(LocalDate due, LocalDate today) {
        if (due != null && due.isBefore(today)) throw new IllegalArgumentException("The due date cannot be in the past");
        return due;
    }

    /** Note on completing: optional, up to 500. */
    static String doneNote(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.length() > 500) throw new IllegalArgumentException("Keep the note under 500 characters");
        return t;
    }

    /** Dismissing needs a reason (5 to 500), so a task is never silently dropped. */
    static String dismissReason(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < 5) throw new IllegalArgumentException("Say why this task is being dismissed (at least 5 characters)");
        if (t.length() > 500) throw new IllegalArgumentException("Keep the reason under 500 characters");
        return t;
    }

    static boolean overdue(String status, LocalDate due, LocalDate today) {
        return "OPEN".equals(status) && due != null && due.isBefore(today);
    }

    /** Only an open task can be closed. */
    static void requireOpen(String status) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("This task is already " + status.toLowerCase());
    }
}
