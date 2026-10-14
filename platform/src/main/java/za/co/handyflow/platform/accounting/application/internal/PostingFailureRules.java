package za.co.handyflow.platform.accounting.application.internal;

/** Rules for the failed-postings queue (finance patch F4, 2026-10-14). Pure, so it is tested without Spring. */
public final class PostingFailureRules {

    public static final int MAX_REASON = 1000;
    public static final int MAX_SUMMARY = 300;

    private PostingFailureRules() {}

    /** The plainest message in the cause chain, cut to fit; the exception's type when nothing says more. */
    public static String reason(Throwable t) {
        if (t == null) return "Unknown error";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = firstNonBlank(root.getMessage(), t.getMessage());
        String text = msg != null ? msg.trim() : root.getClass().getSimpleName();
        return cut(text, MAX_REASON);
    }

    public static String summary(String s) {
        return cut(s == null || s.isBlank() ? "Ledger posting" : s.trim(), MAX_SUMMARY);
    }

    /** The module a key belongs to: the part before the first ':'. */
    public static String module(String sourceKey) {
        if (sourceKey == null) return null;
        int i = sourceKey.indexOf(':');
        return i > 0 ? sourceKey.substring(0, i) : sourceKey;
    }

    /** Null when the failure may be retried here, otherwise why not. */
    public static String retryProblem(String status, boolean hasRequest) {
        if (!"OPEN".equals(status)) return "Only open posting problems can be retried";
        if (!hasRequest)
            return "This failed before a journal could be built. Fix the cause (usually a missing account), then post it again from where it started, or post a journal by hand and dismiss this";
        return null;
    }

    /** Null when the failure may be dismissed with this note, otherwise why not. */
    public static String dismissProblem(String status, String note) {
        if (!"OPEN".equals(status)) return "Only open posting problems can be dismissed";
        if (note == null || note.trim().length() < 5) return "Say why this should not be posted (at least 5 characters)";
        if (note.trim().length() > 500) return "Keep the note to 500 characters";
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
