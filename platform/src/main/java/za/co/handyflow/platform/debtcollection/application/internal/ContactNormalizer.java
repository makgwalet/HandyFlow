package za.co.handyflow.platform.debtcollection.application.internal;

/**
 * Canonical form for matching contact details (opt-outs, inbound senders). Emails are trimmed and
 * lower-cased. Phone numbers keep digits only, and a South African national number (10 digits starting
 * with 0) becomes its 27-prefixed international form, so "082 123 4567", "+27 82 123 4567" and
 * "0821234567" all compare equal. Other formats are matched on their digits as-is.
 */
public final class ContactNormalizer {

    private ContactNormalizer() {}

    public static String normalize(String contact) {
        if (contact == null || contact.isBlank()) {
            throw new IllegalArgumentException("contact is required");
        }
        String c = contact.trim();
        if (c.contains("@")) return c.toLowerCase();
        String digits = c.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("0")) digits = "27" + digits.substring(1);
        if (digits.isEmpty()) throw new IllegalArgumentException("contact has no usable digits or email: " + contact);
        return digits;
    }
}
