package za.co.handyflow.platform.agriculture.domain.rules;

/**
 * A farm's registered brand mark, ear mark and the registration number of the mark (Animal Identification Act 6 of 2002).
 * Kept as short plain text: they are printed on sale documents, so anything that is not printable is refused.
 */
public final class AgMarkRules {

    public static final int MAX_LENGTH = 60;

    private AgMarkRules() {}

    /** Trimmed; blank becomes null (no mark). */
    public static String normalise(String value, String what) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > MAX_LENGTH) throw new IllegalArgumentException(what + " must be at most " + MAX_LENGTH + " characters");
        for (int i = 0; i < v.length(); i++) {
            if (Character.isISOControl(v.charAt(i))) throw new IllegalArgumentException(what + " contains a character that cannot be printed");
        }
        return v;
    }
}
