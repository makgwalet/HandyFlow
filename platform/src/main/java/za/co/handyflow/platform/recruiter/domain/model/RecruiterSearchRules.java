package za.co.handyflow.platform.recruiter.domain.model;

/** Turns what someone typed into a safe contains-pattern. Pure. */
public final class RecruiterSearchRules {
    private RecruiterSearchRules() {}

    public static final int MIN = 2, MAX = 100;

    /** Why this search text cannot be used, or null. Empty text means no search. */
    public static String problem(String q) {
        if (q == null || q.isBlank()) return null;
        int n = q.trim().length();
        if (n < MIN) return "Type at least " + MIN + " characters to search.";
        if (n > MAX) return "The search is too long (" + MAX + " characters at most).";
        return null;
    }

    /** Whether to search at all. */
    public static boolean active(String q) { return q != null && !q.isBlank(); }

    /** A lower-case %text% pattern in which the user's % _ and backslash mean themselves (use with ESCAPE '\'). */
    public static String pattern(String q) {
        String t = q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + t + "%";
    }
}
