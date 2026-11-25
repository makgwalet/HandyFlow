package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * What the practice should be told about a client's tender before it is submitted. The tender's own workflow, requirements, personnel and pricing stay where
 * they are; this only reads the dates and the readiness counts already worked out by the shared readiness rules. Counts, not a score.
 */
public final class TenderWatchRules {

    private TenderWatchRules() {}

    /** Not yet submitted. After submission the practice is waiting on the authority, not preparing. */
    public static final List<String> PREPARING = List.of("DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT");
    public static final int CLOSING_SOON_DAYS = 14;
    public static final int GAPS_WINDOW_DAYS = 30;
    public static final int URGENT_DAYS = 3;
    public static final int EVENT_DAYS = 7;

    public static boolean preparing(String status) { return PREPARING.contains(status); }

    /** True when the readiness check is worth running: preparing, with a closing date inside the window (or already past). */
    public static boolean worthChecking(String status, LocalDate closing, LocalDate today) {
        return preparing(status) && closing != null && ChronoUnit.DAYS.between(today, closing) <= GAPS_WINDOW_DAYS;
    }

    /**
     * One item per tender being prepared. Closed and not submitted, or closing within three days, is high. Closing within two weeks is medium.
     * Between two weeks and a month it appears only if requirements are missing or expired. Further out, or already submitted, there is nothing to say.
     */
    public static WorkQueueRules.Item closing(String tenderName, UUID clientId, String clientName, String status, LocalDate closing, LocalDate today, int missing, int expired) {
        if (!worthChecking(status, closing, today)) return null;
        long days = ChronoUnit.DAYS.between(today, closing);
        int gaps = missing + expired;
        if (days > CLOSING_SOON_DAYS && gaps == 0) return null;
        String when = days < 0 ? "Closing date passed " + closing + " and it has not been submitted" : days == 0 ? "Closes today" : days == 1 ? "Closes tomorrow" : "Closes in " + days + " days";
        String detail = gaps == 0 ? when : when + "; " + gapText(missing, expired);
        String severity = days <= URGENT_DAYS ? WorkQueueRules.HIGH : days <= CLOSING_SOON_DAYS ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW;
        return new WorkQueueRules.Item("TENDER_CLOSING", severity, clientId, clientName, tenderName, detail, closing, "tenders", false);
    }

    public static String gapText(int missing, int expired) {
        StringBuilder b = new StringBuilder();
        if (missing > 0) b.append(missing).append(missing == 1 ? " requirement missing" : " requirements missing");
        if (expired > 0) { if (b.length() > 0) b.append(", "); b.append(expired).append(" expired"); }
        return b.toString();
    }

    /** A briefing or site inspection in the next week, for a tender still being prepared. */
    public static WorkQueueRules.Item event(String tenderName, UUID clientId, String clientName, String status, String what, LocalDate date, LocalDate today) {
        if (!preparing(status) || date == null) return null;
        long days = ChronoUnit.DAYS.between(today, date);
        if (days < 0 || days > EVENT_DAYS) return null;
        String detail = what + (days == 0 ? " is today" : days == 1 ? " is tomorrow" : " is in " + days + " days (" + date + ")");
        return new WorkQueueRules.Item("TENDER_EVENT", days <= 2 ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, clientId, clientName, tenderName, detail, date, "tenders", false);
    }
}
