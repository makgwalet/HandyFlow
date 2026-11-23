package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.ArrayList;
import java.util.List;

/**
 * What a guard phone must be like, and whether a given phone is. Pure logic, report only.
 *
 * A phone is NON_COMPLIANT when something the policy asks for is known to be wrong, UNKNOWN when nothing is known to be wrong but something the
 * policy asks for has never been reported, COMPLIANT otherwise. "Never told us" is never treated as "fine" and never as "failed".
 */
final class DeviceComplianceRules {

    enum Violation { APP_TOO_OLD, OS_TOO_OLD, NO_SCREEN_LOCK, ROOTED }

    enum Missing { APP_VERSION, OS_VERSION, SCREEN_LOCK, ROOT_STATUS }

    enum Status { COMPLIANT, NON_COMPLIANT, UNKNOWN }

    record Policy(String minAppVersion, Integer minOsMajor, boolean requireScreenLock, boolean blockRooted) {
        static Policy none() { return new Policy(null, null, false, false); }

        boolean asksAnything() { return minAppVersion != null || minOsMajor != null || requireScreenLock || blockRooted; }
    }

    record Facts(String appVersion, String osVersion, Boolean screenLockOn, Boolean rooted) {}

    record Result(Status status, List<Violation> violations, List<Missing> missing) {}

    static final int MAX_VERSION_LENGTH = 30;

    private DeviceComplianceRules() {}

    /** Checks what the admin typed. Empty means "not required". Anything else is refused with a message, never quietly changed. */
    static Policy cleanPolicy(String minAppVersion, Integer minOsMajor, Boolean requireScreenLock, Boolean blockRooted) {
        String app = minAppVersion == null ? null : minAppVersion.trim();
        if (app != null && app.isEmpty()) app = null;
        if (app != null && !app.matches("[0-9]+(\\.[0-9]+){0,3}")) {
            throw bad("The lowest app version must be numbers separated by dots, like 2.4 or 3.10.1.", "DEVICE_POLICY_APP_VERSION");
        }
        if (app != null && app.length() > MAX_VERSION_LENGTH) {
            throw bad("The lowest app version is too long.", "DEVICE_POLICY_APP_VERSION");
        }
        if (minOsMajor != null && (minOsMajor < 1 || minOsMajor > 99)) {
            throw bad("The lowest Android or iOS version must be a whole number from 1 to 99.", "DEVICE_POLICY_OS_VERSION");
        }
        return new Policy(app, minOsMajor, Boolean.TRUE.equals(requireScreenLock), Boolean.TRUE.equals(blockRooted));
    }

    /** What a phone tells us about its operating system. Junk is dropped (unknown) rather than stored. */
    static String cleanOsVersion(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty() || t.length() > MAX_VERSION_LENGTH) return null;
        return t.matches("[A-Za-z0-9 ._-]+") && osMajor(t) != null ? t : null;
    }

    /** The first run of digits: "14" -> 14, "Android 14" -> 14, "17.4.1" -> 17. Null when there is none or it is absurd. */
    static Integer osMajor(String v) {
        if (v == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{1,3}").matcher(v);
        if (!m.find()) return null;
        int n = Integer.parseInt(m.group());
        return n >= 1 && n <= 99 ? n : null;
    }

    static Result evaluate(Policy p, Facts f) {
        List<Violation> bad = new ArrayList<>();
        List<Missing> gaps = new ArrayList<>();
        if (p.minAppVersion() != null) {
            if (f.appVersion() == null || f.appVersion().isBlank()) gaps.add(Missing.APP_VERSION);
            else if (FleetHealthRules.compareVersions(f.appVersion(), p.minAppVersion()) < 0) bad.add(Violation.APP_TOO_OLD);
        }
        if (p.minOsMajor() != null) {
            Integer major = osMajor(f.osVersion());
            if (major == null) gaps.add(Missing.OS_VERSION);
            else if (major < p.minOsMajor()) bad.add(Violation.OS_TOO_OLD);
        }
        if (p.requireScreenLock()) {
            if (f.screenLockOn() == null) gaps.add(Missing.SCREEN_LOCK);
            else if (!f.screenLockOn()) bad.add(Violation.NO_SCREEN_LOCK);
        }
        if (p.blockRooted()) {
            if (f.rooted() == null) gaps.add(Missing.ROOT_STATUS);
            else if (f.rooted()) bad.add(Violation.ROOTED);
        }
        Status s = !bad.isEmpty() ? Status.NON_COMPLIANT : !gaps.isEmpty() ? Status.UNKNOWN : Status.COMPLIANT;
        return new Result(s, List.copyOf(bad), List.copyOf(gaps));
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
