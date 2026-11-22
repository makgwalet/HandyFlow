package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Who may report for a phone, and when a report is worth writing. Pure logic. */
final class HeartbeatRules {

    enum Access { OK, NOT_YOURS, INACTIVE }

    /** Reports closer together than this, with nothing new in them, are not written. */
    static final Duration MIN_GAP = Duration.ofSeconds(60);
    static final int MAX_VERSION_LENGTH = 30;

    private HeartbeatRules() {}

    /** A phone bound to another guard looks like it does not exist; a shared phone (no guard) may be reported by any guard. */
    static Access access(UUID deviceGuardId, boolean deviceActive, UUID reportingGuardId) {
        if (deviceGuardId != null && !deviceGuardId.equals(reportingGuardId)) return Access.NOT_YOURS;
        return deviceActive ? Access.OK : Access.INACTIVE;
    }

    static boolean shouldWrite(Instant lastSeen, Instant now, Integer oldBattery, Integer newBattery,
                               String oldVersion, String newVersion) {
        if (lastSeen == null || !lastSeen.isAfter(now.minus(MIN_GAP))) return true;
        if (newBattery != null && !newBattery.equals(oldBattery)) return true;
        return newVersion != null && !newVersion.isBlank() && !newVersion.trim().equals(oldVersion);
    }

    static Integer cleanBattery(Integer b) {
        if (b == null) return null;
        return Math.max(0, Math.min(100, b));
    }

    static String cleanVersion(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > MAX_VERSION_LENGTH ? t.substring(0, MAX_VERSION_LENGTH) : t;
    }
}
