package za.co.handyflow.platform.warehousing.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Lot and expiry rules. Pure, no I/O. Dates are calendar days in the warehouse's own time zone, supplied by the caller. */
public final class WhseLotRules {

    public static final String NO_EXPIRY = "NO_EXPIRY";
    public static final String OK = "OK";
    public static final String EXPIRING = "EXPIRING";
    public static final String EXPIRED = "EXPIRED";

    private WhseLotRules() {}

    /** Whole days from today until the expiry date. Negative once expired, null when the lot does not expire. */
    public static Long daysToExpiry(LocalDate expiry, LocalDate today) {
        return expiry == null ? null : ChronoUnit.DAYS.between(today, expiry);
    }

    /** A lot is EXPIRED the day after its expiry date; on the expiry date itself it is still usable. */
    public static String expiryStatus(LocalDate expiry, LocalDate today, int warnDays) {
        if (expiry == null) return NO_EXPIRY;
        long d = ChronoUnit.DAYS.between(today, expiry);
        if (d < 0) return EXPIRED;
        return d <= warnDays ? EXPIRING : OK;
    }

    /**
     * Whether stock of this lot may be sent out today: not expired, and with at least {@code minShelfLifeDays} still to run
     * (the days a client's customer needs to be able to sell it). A lot without an expiry date is always sellable.
     */
    public static boolean sellable(LocalDate expiry, LocalDate today, Integer minShelfLifeDays) {
        if (expiry == null) return true;
        long remaining = ChronoUnit.DAYS.between(today, expiry);
        if (remaining < 0) return false;
        return minShelfLifeDays == null || remaining >= minShelfLifeDays;
    }

    /** Checks what was typed for a lot against what the item requires. Returns the trimmed lot number. */
    public static String validateLotInput(boolean lotTracked, boolean expiryTracked, String lotNumber, LocalDate expiry,
                                          LocalDate manufactured) {
        String lot = lotNumber == null || lotNumber.isBlank() ? null : lotNumber.trim();
        if (!lotTracked) {
            if (lot != null || expiry != null || manufactured != null) {
                throw new IllegalArgumentException("This item is not lot tracked, so it takes no lot number or dates.");
            }
            return null;
        }
        if (lot == null) throw new IllegalArgumentException("This item is lot tracked. Enter the lot number.");
        if (lot.length() > 60) throw new IllegalArgumentException("A lot number can be at most 60 characters.");
        if (expiryTracked && expiry == null) throw new IllegalArgumentException("This item expires. Enter the expiry date of the lot.");
        if (!expiryTracked && expiry != null) throw new IllegalArgumentException("This item does not expire, so it takes no expiry date.");
        if (manufactured != null && expiry != null && expiry.isBefore(manufactured)) {
            throw new IllegalArgumentException("The expiry date cannot be before the manufactured date.");
        }
        return lot;
    }
}
