package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Contact between a client's portal users and the practice. Pure, so the limits and the status moves can be tested.
 * A client cannot close a query; only the practice can. Limits on open queries and thread length are there to stop a thread or a
 * portal account from filling the practice's queue with noise.
 */
public final class ContactRules {

    private ContactRules() {}

    public static final int MAX_SUBJECT = 200;
    public static final int MAX_BODY = 4000;
    public static final int MAX_OPEN_PER_PERSON = 5;
    public static final int MAX_MESSAGES = 60;
    public static final int MIN_NOTE = 10;
    public static final int WAITING_DAYS = 2;

    public static String text(String raw, String what, int max, boolean required) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            if (required) throw new IllegalArgumentException(what + " is required");
            return null;
        }
        if (t.length() > max) throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        return t;
    }

    public static String subject(String raw) { return text(raw, "A subject", MAX_SUBJECT, true); }

    public static String body(String raw) { return text(raw, "A message", MAX_BODY, true); }

    public static String closeNote(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_NOTE) throw new IllegalArgumentException("Say why in at least " + MIN_NOTE + " characters");
        if (t.length() > MAX_BODY) throw new IllegalArgumentException("The note is too long (at most " + MAX_BODY + " characters)");
        return t;
    }

    /** A new query is refused when its author already has the most they may have open. */
    public static void checkCanRaise(int openForPerson) {
        if (openForPerson >= MAX_OPEN_PER_PERSON) {
            throw new IllegalStateException("You already have " + MAX_OPEN_PER_PERSON + " queries waiting for an answer. Wait for a reply before raising another");
        }
    }

    /** The status after someone writes: the client's message puts it back in the practice's queue, the practice's marks it answered. */
    public static String statusAfter(String current, String authorType) {
        if ("CLOSED".equals(current)) throw new IllegalStateException("This query is closed. Raise a new one if you need to");
        return "CLIENT".equals(authorType) ? "OPEN" : "ANSWERED";
    }

    public static void checkThreadLength(int messageCount) {
        if (messageCount >= MAX_MESSAGES) throw new IllegalStateException("This thread is full. Raise a new query to continue");
    }

    /** Queries nobody has answered for more than two days are medium; newer ones are low. */
    public static WorkQueueRules.Item queryItem(LocalDate today, UUID clientId, String clientName, String subject, Instant lastMessageAt) {
        long days = ChronoUnit.DAYS.between(lastMessageAt.atZone(ZoneOffset.UTC).toLocalDate(), today);
        String detail = days <= 0 ? "Client wrote today" : days == 1 ? "Waiting for an answer for 1 day" : "Waiting for an answer for " + days + " days";
        return new WorkQueueRules.Item("PORTAL_QUERY", days > WAITING_DAYS ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, clientId, clientName, subject, detail, null, "requests", false);
    }

    /** A document asked for and not yet received. It becomes medium on its due date and high a week after. */
    public static WorkQueueRules.Item requestItem(LocalDate today, UUID clientId, String clientName, String title, LocalDate due) {
        if (due == null) return new WorkQueueRules.Item("DOCUMENT_REQUEST", WorkQueueRules.LOW, clientId, clientName, title, "Asked for, no date set", null, "requests", false);
        long late = ChronoUnit.DAYS.between(due, today);
        String sev = late > 7 ? WorkQueueRules.HIGH : late >= 0 ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW;
        String detail = late > 0 ? "Asked for, " + late + (late == 1 ? " day" : " days") + " late" : late == 0 ? "Asked for, due today" : "Asked for, due " + due;
        return new WorkQueueRules.Item("DOCUMENT_REQUEST", sev, clientId, clientName, title, detail, due, "requests", false);
    }

    public static LocalDate dueDate(LocalDate due, LocalDate today) {
        if (due != null && due.isBefore(today)) throw new IllegalArgumentException("The date asked for is in the past");
        return due;
    }

    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final java.util.Set<String> UPLOAD_EXTENSIONS = java.util.Set.of("pdf", "jpg", "jpeg", "png", "xlsx", "docx", "csv");

    /** What a client may upload from the portal: an allowed kind of file, not empty, not over 10 MB. Judged by the file name's ending. */
    public static void checkUpload(String fileName, long size) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (!UPLOAD_EXTENSIONS.contains(ext)) throw new IllegalArgumentException("That kind of file cannot be uploaded. Use PDF, JPG, PNG, XLSX, DOCX or CSV");
        if (size <= 0) throw new IllegalArgumentException("That file is empty");
        if (size > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("That file is too large. The most you can upload is 10 MB");
    }
}
