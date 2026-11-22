package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** What is wrong with a phone or tablet, from what it last reported. Pure logic. */
final class FleetHealthRules {

    enum Issue { OFFLINE, NEVER_SEEN, CRITICAL_BATTERY, LOW_BATTERY, OLD_APP }

    record Config(int offlineMinutes, int neverSeenHours, int lowBattery, int criticalBattery, String minAppVersion) {
        static Config defaults() { return new Config(30, 24, 20, 10, null); }
    }

    record Facts(boolean active, Instant lastSeenAt, Instant createdAt, Integer batteryPct, String appVersion, boolean onSession) {}

    /** A battery reading older than this is not trusted (the phone is reported as quiet instead). */
    static final Duration BATTERY_FRESH = Duration.ofHours(2);

    private FleetHealthRules() {}

    static List<Issue> assess(Facts f, Config c, Instant now) {
        List<Issue> out = new ArrayList<>();
        if (!f.active()) return out;   // revoked, lost or not yet activated phones are not "unhealthy", they are out of service
        boolean seen = f.lastSeenAt() != null;
        if (f.onSession()) {
            // Someone is on shift with this phone: silence is the alarm.
            if (!seen || f.lastSeenAt().isBefore(now.minus(Duration.ofMinutes(c.offlineMinutes())))) out.add(Issue.OFFLINE);
        } else if (!seen && f.createdAt() != null && f.createdAt().isBefore(now.minus(Duration.ofHours(c.neverSeenHours())))) {
            out.add(Issue.NEVER_SEEN);
        }
        boolean freshBattery = seen && f.batteryPct() != null && !f.lastSeenAt().isBefore(now.minus(BATTERY_FRESH));
        if (freshBattery) {
            if (f.batteryPct() <= c.criticalBattery()) out.add(Issue.CRITICAL_BATTERY);
            else if (f.batteryPct() <= c.lowBattery()) out.add(Issue.LOW_BATTERY);
        }
        if (c.minAppVersion() != null && !c.minAppVersion().isBlank() && seen
                && compareVersions(f.appVersion(), c.minAppVersion()) < 0) {
            out.add(Issue.OLD_APP);
        }
        return out;
    }

    /** When the phone was last known to be alive: its last check-in, or the start of the open session if that is later. */
    static Instant effectiveLastSeen(Instant lastSeen, Instant openSessionStart) {
        if (lastSeen == null) return openSessionStart;
        if (openSessionStart == null) return lastSeen;
        return openSessionStart.isAfter(lastSeen) ? openSessionStart : lastSeen;
    }

    static boolean critical(Issue i) { return i == Issue.OFFLINE || i == Issue.CRITICAL_BATTERY; }

    /** Dotted numeric versions ("2.10.1"). A missing or unreadable version counts as older than anything. */
    static int compareVersions(String have, String need) {
        int[] a = parse(have), b = parse(need);
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parse(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        String[] parts = t.split("\\.");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i].replaceAll("[^0-9].*$", "");   // "3-beta" -> 3
                out[i] = Integer.parseInt(p);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out.length == 0 ? null : out;
    }
}
