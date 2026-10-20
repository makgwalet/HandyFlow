package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a vehicle is in its life as a fleet asset — separate from its day-to-day
 * operational {@link VehicleStatus}. A vehicle can be lifecycle ACTIVE while operationally
 * BREAKDOWN or MAINTENANCE; that is exactly why they are two fields.
 * <p>
 * Deliberately NOT in this enum (they already exist elsewhere, or are operational):
 * MAINTENANCE and BREAKDOWN (operational status), ASSIGNED (Vehicle.assignedDriverId),
 * ACCIDENT (the accident register — an accident does not change what the asset is).
 * <p>
 * Out-of-fleet states (SOLD, TRANSFERRED, WRITTEN_OFF, RETIRED, DISPOSED) also force the
 * operational status to RETIRED, so existing "retired" filters and the compliance
 * scheduler's RETIRED exclusion keep working unchanged.
 */
public enum VehicleLifecycleStatus {
    ORDERED, ACQUIRED, ACTIVE, STOLEN, MISSING, IMPOUNDED, WRITTEN_OFF, SOLD, TRANSFERRED, RETIRED, DISPOSED;

    private static final Map<VehicleLifecycleStatus, Set<VehicleLifecycleStatus>> ALLOWED =
            new EnumMap<>(VehicleLifecycleStatus.class);

    static {
        ALLOWED.put(ORDERED, EnumSet.of(ACQUIRED, RETIRED));                // RETIRED = order cancelled
        ALLOWED.put(ACQUIRED, EnumSet.of(ACTIVE, RETIRED));
        ALLOWED.put(ACTIVE, EnumSet.of(STOLEN, MISSING, IMPOUNDED, WRITTEN_OFF, SOLD, TRANSFERRED, RETIRED));
        ALLOWED.put(STOLEN, EnumSet.of(ACTIVE, WRITTEN_OFF));               // ACTIVE = recovered
        ALLOWED.put(MISSING, EnumSet.of(ACTIVE, STOLEN, WRITTEN_OFF));
        ALLOWED.put(IMPOUNDED, EnumSet.of(ACTIVE, WRITTEN_OFF, DISPOSED));  // ACTIVE = released
        ALLOWED.put(WRITTEN_OFF, EnumSet.of(DISPOSED));                     // salvage sold / scrapped
        ALLOWED.put(RETIRED, EnumSet.of(SOLD, TRANSFERRED, DISPOSED));
        ALLOWED.put(SOLD, EnumSet.noneOf(VehicleLifecycleStatus.class));
        ALLOWED.put(TRANSFERRED, EnumSet.noneOf(VehicleLifecycleStatus.class));
        ALLOWED.put(DISPOSED, EnumSet.noneOf(VehicleLifecycleStatus.class));
    }

    public boolean canTransitionTo(VehicleLifecycleStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Only an ACTIVE vehicle can be put to work (start trips, etc.). */
    public boolean isOperating() {
        return this == ACTIVE;
    }

    /** No longer part of the working fleet. */
    public boolean isOutOfFleet() {
        return this == SOLD || this == TRANSFERRED || this == WRITTEN_OFF || this == RETIRED || this == DISPOSED;
    }
}