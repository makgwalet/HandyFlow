package za.co.handyflow.platform.security.application.internal;

/**
 * Masks guard personal data before it leaves the backend, and recognises a masked value coming back in.
 * Pure logic, no Spring.
 */
final class PiiMask {

    static final char STAR = '*';
    static final char DOT = '•';

    private PiiMask() {}

    /** SA ID (13 digits): first 6 digits (date of birth) then stars. Other numbers (passport): last 3 characters only. */
    static String maskId(String id) {
        if (id == null || id.isBlank()) return id;
        String digits = id.replaceAll("\\D", "");
        if (digits.length() == 13 && digits.equals(id.trim())) {
            return digits.substring(0, 6) + String.valueOf(STAR).repeat(7);
        }
        String t = id.trim();
        if (t.length() <= 3) return String.valueOf(STAR).repeat(t.length());
        return String.valueOf(STAR).repeat(t.length() - 3) + t.substring(t.length() - 3);
    }

    /** Bank account: bullets then the last 4 characters. */
    static String maskAccount(String account) {
        if (account == null || account.isBlank()) return account;
        String t = account.trim();
        if (t.length() <= 4) return String.valueOf(DOT).repeat(t.length());
        return String.valueOf(DOT).repeat(t.length() - 4) + t.substring(t.length() - 4);
    }

    /** True when the text is a masked value (it contains a mask character). Real IDs and accounts never do. */
    static boolean isMasked(String s) {
        return s != null && (s.indexOf(STAR) >= 0 || s.indexOf(DOT) >= 0);
    }

    /**
     * What to store when an update arrives: a blank or masked incoming value means "unchanged", because the
     * screen only ever saw the masked text. Returns the existing value in those cases.
     */
    static String keepIfMasked(String incoming, String existing) {
        if (incoming == null || incoming.isBlank() || isMasked(incoming)) return existing;
        return incoming;
    }
}
