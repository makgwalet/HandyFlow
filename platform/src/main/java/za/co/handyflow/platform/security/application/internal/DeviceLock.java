package za.co.handyflow.platform.security.application.internal;

/** Is a login coming from the device the guard is bound to? Pure. */
final class DeviceLock {

    enum Verdict { OK, MISMATCH, REQUIRED }

    private DeviceLock() {}

    /**
     * A guard not bound to a device is unrestricted. A bound guard must say which
     * device they are on, and it must be theirs; leaving the id out is not a way round the lock.
     */
    static Verdict check(String registeredDeviceId, String suppliedDeviceId) {
        if (registeredDeviceId == null || registeredDeviceId.isBlank()) return Verdict.OK;
        if (suppliedDeviceId == null || suppliedDeviceId.isBlank()) return Verdict.REQUIRED;
        return registeredDeviceId.equals(suppliedDeviceId) ? Verdict.OK : Verdict.MISMATCH;
    }
}
