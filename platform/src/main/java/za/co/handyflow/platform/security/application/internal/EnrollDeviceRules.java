package za.co.handyflow.platform.security.application.internal;

import java.util.UUID;

/**
 * What enrolling a guard on a phone should do when that phone is already known to the tenant.
 * Before this, enrolment just set the existing row ACTIVE: a phone marked LOST, REVOKED or BLOCKED came back to life,
 * a shared site device became a guard's "own" phone, and another guard's id stayed on the row.
 */
final class EnrollDeviceRules {

    enum Action { CREATE, KEEP, REBIND, REFUSE_UNAVAILABLE, REFUSE_SHARED, REFUSE_OTHER_GUARD }

    private EnrollDeviceRules() {}

    /**
     * @param exists       whether the tenant already has a row for this hardware id
     * @param type         PERSONAL_GUARD_DEVICE or SHARED_SITE_DEVICE (existing row)
     * @param status       PENDING, ACTIVE, REVOKED, LOST, BLOCKED, REPLACED, DECOMMISSIONED (existing row)
     * @param rowGuardId   guard the existing row belongs to (may be null)
     * @param guardId      guard being enrolled
     */
    static Action decide(boolean exists, String type, String status, UUID rowGuardId, UUID guardId) {
        if (!exists) return Action.CREATE;
        if ("SHARED_SITE_DEVICE".equals(type)) return Action.REFUSE_SHARED;
        if ("LOST".equals(status) || "REVOKED".equals(status) || "BLOCKED".equals(status)
                || "DECOMMISSIONED".equals(status)) return Action.REFUSE_UNAVAILABLE;
        if (rowGuardId == null || rowGuardId.equals(guardId)) return Action.KEEP;
        // Another guard's phone: only a phone that guard has already been moved off may be handed on.
        return "REPLACED".equals(status) ? Action.REBIND : Action.REFUSE_OTHER_GUARD;
    }

    /** The guard's previous phone should be closed as REPLACED when they are now enrolled on a different one. */
    static boolean retirePrevious(String previousHardwareId, String newHardwareId) {
        return previousHardwareId != null && !previousHardwareId.equals(newHardwareId);
    }
}
