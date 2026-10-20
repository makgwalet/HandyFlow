package za.co.handyflow.platform.fleet.domain.model;

/**
 * What happened. SPEEDING, EXCESSIVE_IDLING and GEOFENCE_* are derived by our own rules from
 * positions; the harsh-driving types and DEVICE_UNPLUGGED come from the device itself (they need an
 * accelerometer / tamper sensor) and arrive through the provider's event feed. A provider may also
 * send any of the derived types natively.
 */
public enum TelematicsEventType {
    SPEEDING, HARSH_BRAKING, HARSH_ACCELERATION, HARSH_CORNERING, EXCESSIVE_IDLING,
    GEOFENCE_ENTER, GEOFENCE_EXIT, DEVICE_UNPLUGGED
}