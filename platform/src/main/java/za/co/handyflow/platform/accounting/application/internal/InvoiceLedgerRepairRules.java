package za.co.handyflow.platform.accounting.application.internal;

import java.time.LocalDate;

/** Which date a missing issuance journal gets, and when it cannot be posted at all. Pure. */
public final class InvoiceLedgerRepairRules {

    public static final String ORIGINAL = "ORIGINAL", TODAY = "TODAY";

    private InvoiceLedgerRepairRules() {}

    public static boolean validPolicy(String policy) { return ORIGINAL.equals(policy) || TODAY.equals(policy); }

    /** The invoice's own issue date, or today. An invoice with no recorded issue date falls back to today. */
    public static LocalDate entryDate(String policy, LocalDate issuedOn, LocalDate today) {
        return ORIGINAL.equals(policy) && issuedOn != null ? issuedOn : today;
    }

    /** Why the journal cannot be posted on {@code date}, or null. A date on or before the lock date is closed. */
    public static String skipReason(LocalDate date, LocalDate lockedThrough) {
        if (lockedThrough != null && !date.isAfter(lockedThrough))
            return "The books are locked through " + lockedThrough + ", so nothing can be dated " + date + ".";
        return null;
    }
}
