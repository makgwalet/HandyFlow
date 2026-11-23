package za.co.handyflow.platform.security.application.internal;

/** When a guard's open clock-in session must be closed because of a status change. Pure. */
final class SessionOffboarding {

    /** Matches `security_device_sessions.forced_close_reason` (varchar 200). */
    static final int REASON_MAX = 200;

    private SessionOffboarding() {}

    /** Anything but ACTIVE: suspended, on leave, terminated. The guard's token is revoked for the same statuses. */
    static boolean mustClose(String newStatus) {
        return newStatus != null && !"ACTIVE".equals(newStatus);
    }

    static String reasonForStatus(String newStatus) {
        return clip("Guard status set to " + (newStatus == null ? "unknown" : newStatus) + ": session closed by the system");
    }

    static final String REASON_DELETED = "Guard deleted: session closed by the system";

    static String clip(String s) { return s.length() <= REASON_MAX ? s : s.substring(0, REASON_MAX); }
}
