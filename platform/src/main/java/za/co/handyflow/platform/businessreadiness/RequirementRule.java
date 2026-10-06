package za.co.handyflow.platform.businessreadiness;

/**
 * What satisfies a requirement, set on the tracked requirement by the user (so regulatory knowledge is configuration, not code).
 * <ul>
 *   <li>{@code authority} and/or {@code registrationType}: a registration must exist and be valid. A blank part matches anything.</li>
 *   <li>{@code documentType}: a document of this type must exist, be unexpired and be verified.</li>
 * </ul>
 * Both can be set, in which case both must hold. Matching ignores case and surrounding spaces.
 */
public record RequirementRule(String authority, String registrationType, String documentType) {

    public boolean needsRegistration() { return !blank(authority) || !blank(registrationType); }

    public boolean needsDocument() { return !blank(documentType); }

    /** A rule with nothing in it cannot be evaluated. */
    public boolean isEmpty() { return !needsRegistration() && !needsDocument(); }

    static boolean blank(String s) { return s == null || s.isBlank(); }
}
