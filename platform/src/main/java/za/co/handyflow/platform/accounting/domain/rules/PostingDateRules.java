package za.co.handyflow.platform.accounting.domain.rules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The ledger date of a journal that records a business document (audit finding 1.6): the day the document was issued
 * in the company's own time zone, never the day the listener happens to run, and never the server's zone.
 */
public final class PostingDateRules {

    private PostingDateRules() {}

    /**
     * @param zone       the company's zone
     * @param occurredOn when the document was issued (the event's time); may be missing on very old events
     * @param today      the company's business day now, used only when {@code occurredOn} is missing
     */
    public static LocalDate documentDate(ZoneId zone, Instant occurredOn, LocalDate today) {
        if (occurredOn == null) return today;
        return occurredOn.atZone(zone).toLocalDate();
    }
}
