package za.co.handyflow.platform.recruiter.domain.model;

/** When a queued email is tried again and when it is given up on. Pure. */
public final class RecruiterOutboxRules {
    private RecruiterOutboxRules() {}

    public static final String PENDING = "PENDING", SENT = "SENT", FAILED = "FAILED";
    public static final int MAX_ATTEMPTS = 5, MAX_ERROR = 300;
    /** Minutes to wait after the 1st, 2nd, 3rd and 4th failed attempt. */
    private static final int[] BACKOFF_MINUTES = {1, 5, 30, 120};

    /** The status after an attempt: sent, still waiting for another try, or given up. */
    public static String statusAfter(int attemptsMade, boolean succeeded) {
        if (succeeded) return SENT;
        return attemptsMade >= MAX_ATTEMPTS ? FAILED : PENDING;
    }

    /** Minutes until the next try after this many failed attempts. */
    public static int delayMinutes(int attemptsMade) {
        int i = Math.max(1, Math.min(attemptsMade, BACKOFF_MINUTES.length)) - 1;
        return BACKOFF_MINUTES[i];
    }

    /** Short, single-line text safe to store and show. */
    public static String errorText(Throwable t) {
        if (t == null) return null;
        String m = t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getMessage();
        m = m.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return m.length() > MAX_ERROR ? m.substring(0, MAX_ERROR) : m;
    }

    public static String retryProblem(String status) {
        return FAILED.equals(status) ? null : "Only an email that could not be sent can be tried again.";
    }

    /** Whether there is somewhere to send it. */
    public static boolean deliverable(String email) {
        return email != null && email.length() <= 254 && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
