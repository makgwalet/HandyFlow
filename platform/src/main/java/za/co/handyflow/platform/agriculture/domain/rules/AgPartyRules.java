package za.co.handyflow.platform.agriculture.domain.rules;

/** Names, ID numbers, addresses and vehicle registrations typed onto a sale: trimmed, one line, length-limited. */
public final class AgPartyRules {

    public static final int NAME = 200, ID_NUMBER = 60, ADDRESS = 300, VEHICLE = 30;

    private AgPartyRules() {}

    /** Trimmed; blank becomes null; no control characters (including line breaks); at most {@code max} characters. */
    public static String clean(String value, String what, int max) {
        if (value == null) return null;
        String v = value.strip();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new IllegalArgumentException(what + " must be at most " + max + " characters");
        for (int i = 0; i < v.length(); i++) {
            if (Character.isISOControl(v.charAt(i))) throw new IllegalArgumentException(what + " must be on one line and use printable characters only");
        }
        return v;
    }

    /** A vehicle registration is kept upper case, so "ca 123-456" and "CA 123-456" are the same. */
    public static String vehicle(String value) {
        String v = clean(value, "Vehicle registration", VEHICLE);
        return v == null ? null : v.toUpperCase(java.util.Locale.ROOT);
    }
}
