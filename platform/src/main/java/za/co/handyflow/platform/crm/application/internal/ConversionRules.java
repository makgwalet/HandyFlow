package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.CustomerType;

/** When a conversion may be undone. Pure, no database: the service counts the bookings and invoices and asks. */
final class ConversionRules {
    static final int REASON_MAX = 500;

    private ConversionRules() {}

    /** Throws the first reason the conversion cannot be undone. A customer who has started to trade is no longer a lead. */
    static void checkUndo(CustomerType type, long activeBookings, long unpaidInvoices) {
        if (type != CustomerType.CUSTOMER) throw new IllegalStateException("This record is already a lead");
        if (activeBookings > 0) throw new IllegalStateException(activeBookings + (activeBookings == 1 ? " active booking exists" : " active bookings exist") + ". Cancel or complete " + (activeBookings == 1 ? "it" : "them") + " first.");
        if (unpaidInvoices > 0) throw new IllegalStateException(unpaidInvoices + (unpaidInvoices == 1 ? " unpaid invoice exists" : " unpaid invoices exist") + ". Settle or void " + (unpaidInvoices == 1 ? "it" : "them") + " first.");
    }

    /** The reason as stored: trimmed, blank means none, and too long is refused. */
    static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String t = reason.strip();
        if (t.length() > REASON_MAX) throw new IllegalArgumentException("The reason can be at most " + REASON_MAX + " characters");
        return t;
    }
}
