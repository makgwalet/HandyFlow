package za.co.handyflow.platform.security.application.internal;

/**
 * A shared site device (one phone or tablet at a post, used by whichever guard is on duty) behaves differently from a
 * guard's own phone. Pure.
 */
final class SharedDeviceRules {

    private SharedDeviceRules() {}

    /** True when this login comes from a registered, ACTIVE shared site device of the guard's own company. */
    static boolean isSharedLogin(boolean deviceFound, boolean sharedType, boolean active) {
        return deviceFound && sharedType && active;
    }

    /**
     * The device a guard is "tied" to for lock purposes. On a shared device the guard's personal phone is irrelevant:
     * logging in there must neither be refused as DEVICE_MISMATCH nor get the registered-device lock bypass.
     */
    static String effectiveRegisteredDevice(boolean sharedLogin, String guardRegisteredDeviceId) {
        return sharedLogin ? null : guardRegisteredDeviceId;
    }
}
