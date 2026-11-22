// security/application/internal/PackWording.java
package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The plain-language sentences of the monthly service report, the same as the web page prints. Pure. */
public final class PackWording {

    private PackWording() {}

    static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    static final Map<String, String> SHIFT_LABEL = new java.util.LinkedHashMap<>();
    static {
        SHIFT_LABEL.put("COMPLETED", "Completed"); SHIFT_LABEL.put("SCHEDULED", "Scheduled"); SHIFT_LABEL.put("ACTIVE", "In progress");
        SHIFT_LABEL.put("MISSED", "Missed"); SHIFT_LABEL.put("CANCELLED", "Cancelled"); SHIFT_LABEL.put("PULLED", "Pulled early");
    }

    /** 47.0 prints as 47, 97.9 as 97.9, the way the web page shows numbers. */
    static String num(double v) { return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v); }

    static String titleCase(String s) { return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase().replace('_', ' '); }

    public static String cover(boolean measured, int postDays, double required, double covered, Double pct) {
        if (!measured) return "No posts are set up for this site, so cover is not measured.";
        if (postDays == 0) return "No post-days had finished in this period.";
        return num(covered) + " of " + num(required) + " required guard-hours covered" + (pct == null ? "" : " (" + num(pct) + "%)") + ".";
    }

    public static String coverDetail(boolean measured, int postDays, int short_, int uncovered) {
        if (!measured || postDays == 0) return "";
        List<String> p = new ArrayList<>();
        p.add(postDays + " post-days measured");
        if (short_ > 0) p.add(short_ + " short");
        if (uncovered > 0) p.add(uncovered + " with no one rostered");
        return String.join(", ", p) + ".";
    }

    public static String patrols(int rounds, int complete, int partial, int missed, int expected, int scanned, Double pct) {
        if (rounds == 0) return "No patrol rounds were due.";
        return complete + " of " + rounds + " rounds complete, " + partial + " partial, " + missed + " missed. "
                + scanned + " of " + expected + " checkpoints scanned" + (pct == null ? "" : " (" + num(pct) + "%)") + ".";
    }

    public static String incidents(int total, int resolved, Map<String, Integer> bySeverity) {
        if (total == 0) return "No incidents.";
        List<String> sev = new ArrayList<>();
        for (String s : SEVERITY_ORDER) { Integer n = bySeverity.get(s); if (n != null && n > 0) sev.add(n + " " + titleCase(s)); }
        return total + (total == 1 ? " incident" : " incidents") + " (" + String.join(", ", sev) + "), " + resolved + " resolved.";
    }

    public static String visitors(int total, int withVehicle, Map<String, Integer> byType) {
        if (total == 0) return "No visitors or contractors logged.";
        List<String> t = new ArrayList<>();
        for (var e : new TreeMap<>(byType).entrySet()) t.add(e.getValue() + " " + titleCase(e.getKey()));
        return total + " logged (" + String.join(", ", t) + "), " + withVehicle + " with a vehicle.";
    }

    public static String book(int daysSigned, int daysInPeriod) {
        return "Occurrence Book signed off on " + daysSigned + " of " + daysInPeriod + " days.";
    }

    public static String shifts(Map<String, Integer> byStatus) {
        List<String> p = new ArrayList<>();
        for (var e : SHIFT_LABEL.entrySet()) { Integer n = byStatus.get(e.getKey()); if (n != null && n > 0) p.add(n + " " + e.getValue().toLowerCase()); }
        return p.isEmpty() ? "No shifts." : String.join(", ", p) + ".";
    }
}
