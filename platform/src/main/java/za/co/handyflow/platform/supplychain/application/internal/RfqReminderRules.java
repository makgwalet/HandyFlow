package za.co.handyflow.platform.supplychain.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Pure timing rules for RFQ and quotation reminders. Each reminder is sent once; the marker column is the memory. */
public final class RfqReminderRules {

    private RfqReminderRules() {}

    public static final int SUPPLIER_HOURS_BEFORE_CLOSE = 48;
    public static final int QUOTATION_EXPIRY_DAYS = 7;

    /** An invited supplier who has not answered, within {@code hours} of an OPEN RFQ's close, and has not been reminded yet. */
    public static boolean supplierReminderDue(String invitationStatus, Instant reminderSentAt, String rfqStatus, Instant closesAt, Instant now, int hours) {
        if (!"INVITED".equals(invitationStatus) || reminderSentAt != null || !"OPEN".equals(rfqStatus) || closesAt == null) return false;
        return closesAt.isAfter(now) && !closesAt.isAfter(now.plus(Duration.ofHours(hours)));
    }

    /** An RFQ whose closing time has passed but which nobody has closed for evaluation, not yet notified. */
    public static boolean buyerCloseNoticeDue(String rfqStatus, Instant closesAt, Instant noticeSentAt, Instant now) {
        return "OPEN".equals(rfqStatus) && closesAt != null && !closesAt.isAfter(now) && noticeSentAt == null;
    }

    /** A submitted quotation on an RFQ still undecided (OPEN or CLOSED) whose validity ends within {@code days}, not yet notified. */
    public static boolean quotationExpiryNoticeDue(String quotationStatus, String rfqStatus, LocalDate validUntil, Instant noticeSentAt, LocalDate today, int days) {
        if (!"SUBMITTED".equals(quotationStatus) || noticeSentAt != null || validUntil == null) return false;
        if (!"OPEN".equals(rfqStatus) && !"CLOSED".equals(rfqStatus)) return false;
        return !validUntil.isBefore(today) && !validUntil.isAfter(today.plusDays(days));
    }
}
