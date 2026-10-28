package za.co.handyflow.platform.recruiter.domain.model;

/**
 * What an applicant agrees to when they apply through the careers page. The wording is versioned so a record of consent says which
 * wording was shown. Change {@link #VERSION} whenever {@link #STATEMENT} changes.
 */
public final class RecruiterConsentRules {

    private RecruiterConsentRules() {}

    public static final String VERSION = "2026-10b";

    public static final String STATEMENT =
            "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, "
            + "and that they will be removed after the company's retention period. I can ask to see, correct or delete them at any time.";

    /**
     * The statement as shown to the applicant: the same words with the company's retention period in months. Without a usable number it is
     * the general statement. Mirrored by the careers page.
     */
    public static String statementFor(Integer retentionMonths) {
        if (retentionMonths == null || retentionMonths < 1) return STATEMENT;
        return "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, "
                + "and that they will be removed " + retentionMonths + (retentionMonths == 1 ? " month" : " months")
                + " after my last activity unless I am hired. I can ask to see, correct or delete them at any time.";
    }

    public static final int MAX_URL = 500;

    /** Null when the link is usable as a privacy-notice link: empty (none), or an http(s) address of reasonable length with a host. */
    public static String privacyUrlProblem(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (u.length() > MAX_URL) return "The privacy notice link is too long (" + MAX_URL + " characters at most).";
        String lower = u.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return "The privacy notice link must start with https:// or http://.";
        String rest = u.substring(lower.startsWith("https://") ? 8 : 7);
        int end = rest.length();
        for (char c : new char[]{'/', '?', '#'}) { int i = rest.indexOf(c); if (i >= 0 && i < end) end = i; }
        String host = rest.substring(0, end);
        if (host.isEmpty() || host.contains("@") || host.chars().anyMatch(Character::isWhitespace) || u.chars().anyMatch(Character::isWhitespace))
            return "That does not look like a web address.";
        return null;
    }

    /** The link as it is stored: trimmed, and null when empty. */
    public static String normaliseUrl(String url) { return url == null || url.isBlank() ? null : url.trim(); }

    /** Null when the application may go ahead. */
    public static String problem(Boolean consent) {
        return Boolean.TRUE.equals(consent) ? null : "Please agree to the use of your details before you apply.";
    }
}
