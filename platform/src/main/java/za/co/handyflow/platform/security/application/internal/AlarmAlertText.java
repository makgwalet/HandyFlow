package za.co.handyflow.platform.security.application.internal;

/**
 * Wording for control-room alerts. Pure, so it is testable without Spring.
 * Keeps the SMS short (one segment) and puts as little personal detail as
 * possible in it: guard first name only, no coordinates.
 */
final class AlarmAlertText {

    static final int SMS_MAX = 160;

    record Text(String title, String message) {}

    private AlarmAlertText() {}

    static Text duress(String guardFirstName, String siteName) {
        String who = clean(guardFirstName, "A guard");
        String where = siteName == null || siteName.isBlank() ? "" : " at " + siteName.trim();
        String title = "PANIC: " + who + where;
        String msg = "PANIC pressed by " + who + where + ". Open Control Room and respond now.";
        return new Text(title, fit(msg));
    }

    static Text critical(String source, String description) {
        String src = clean(source, "alarm").replace('_', ' ').toLowerCase();
        String title = "CRITICAL alarm: " + src;
        String d = description == null || description.isBlank() ? "" : " " + description.trim();
        return new Text(title, fit("CRITICAL " + src + " alarm." + d + " Open Control Room."));
    }

    static Text incident(String title, String siteName) {
        String where = siteName == null || siteName.isBlank() ? "" : " at " + siteName.trim();
        String what = title == null || title.isBlank() ? "" : ": " + title.trim();
        return new Text("CRITICAL incident" + where, fit("CRITICAL incident" + where + what + ". Open Incidents."));
    }

    /** A firearm reported lost or stolen. Serial only; no guard name in the SMS. */
    static Text firearmLost(String serial, boolean hadHolder) {
        String s = clean(serial, "unknown serial");
        String who = hadHolder ? " It was issued to a guard." : "";
        return new Text("Firearm lost or stolen: " + s,
                fit("Firearm " + s + " reported lost or stolen." + who + " Report to SAPS within 24 hours."));
    }

    /** A lost firearm with no SAPS case number after the 24-hour window. */
    static Text sapsOverdue(String serial) {
        String s = clean(serial, "unknown serial");
        return new Text("SAPS report overdue: firearm " + s,
                fit("Lost firearm " + s + " has no SAPS case number and the 24-hour reporting window has passed. Report it now."));
    }

    /** Truncates to one SMS segment, ending with an ellipsis if cut. */
    static String fit(String s) {
        if (s.length() <= SMS_MAX) return s;
        return s.substring(0, SMS_MAX - 1).trim() + "…";
    }

    private static String clean(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
