package za.co.handyflow.platform.security.application.internal;

/** Which state changes a supervisor may make to a guard's personal phone. */
final public class DeviceStateRules {

    public enum Action {
        LOST("LOST"), REVOKE("REVOKED"), REINSTATE("ACTIVE");
        final String target;
        Action(String target) { this.target = target; }
    }

    private DeviceStateRules() {}

    /**
     * LOST: a working phone (ACTIVE/PENDING) goes missing.
     * REVOKE: pull any phone still in play, including one reported lost.
     * REINSTATE: only phones taken out by LOST/REVOKE (found again / revoked by mistake). BLOCKED and DECOMMISSIONED are
     * deliberate end states and REPLACED phones were superseded by a newer one, so none of those come back this way.
     */
    static boolean allowed(Action action, String status) {
        if (action == null || status == null) return false;
        return switch (action) {
            case LOST -> status.equals("ACTIVE") || status.equals("PENDING");
            case REVOKE -> status.equals("ACTIVE") || status.equals("PENDING") || status.equals("LOST");
            case REINSTATE -> status.equals("LOST") || status.equals("REVOKED");
        };
    }

    /** Sessions on the phone end for LOST and REVOKE; reinstating never logs anyone in. */
    static boolean endsSessions(Action action) { return action != Action.REINSTATE; }

    /** A phone can only be reinstated while it is still the guard's current one, or they would be locked to a different phone. */
    static boolean isGuardsCurrentDevice(String guardRegisteredDeviceId, String deviceHardwareId) {
        return guardRegisteredDeviceId != null && guardRegisteredDeviceId.equals(deviceHardwareId);
    }
}
