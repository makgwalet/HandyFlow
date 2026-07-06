package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle state of a fleet vehicle, with explicit legal transitions.
 * <p>
 * WHY this exists: the previous {@code Vehicle.updateStatus(String status)}
 * accepted any string and overwrote the field unconditionally — no
 * validation at all, not even the "at least it's typed" protection a raw
 * String enum-like field sometimes gets elsewhere. That meant a vehicle
 * could go RETIRED → ON_TRIP, or a typo like "ON_TRIPP" would sail through
 * validation and fail silently deep in a switch statement's default case.
 * This is the same fix applied to earthmoving's AssetStatus, for the same
 * reason: make illegal transitions a compile-time-checked, runtime-rejected
 * impossibility instead of trusting every call site to remember the rules.
 * <p>
 * Transition rationale:
 * <ul>
 *   <li>AVAILABLE → ON_TRIP, MAINTENANCE, BREAKDOWN, RETIRED — a parked
 *       vehicle can be dispatched, serviced, found broken, or decommissioned.</li>
 *   <li>ON_TRIP → AVAILABLE, BREAKDOWN — a trip ends normally (AVAILABLE) or
 *       the vehicle breaks down mid-trip (BREAKDOWN). It can NOT go straight
 *       to MAINTENANCE — a trip must be ended first (see FleetService.endTrip),
 *       so there's always an odometer/end-time record of where the trip
 *       actually stopped rather than a vehicle silently vanishing from the
 *       logbook mid-journey.</li>
 *   <li>MAINTENANCE → AVAILABLE, BREAKDOWN, RETIRED — service completes, or
 *       reveals a fault that needs BREAKDOWN handling, or the vehicle is
 *       written off.</li>
 *   <li>BREAKDOWN → MAINTENANCE, RETIRED — a broken vehicle gets fixed or
 *       gets written off. It can NOT go straight back to AVAILABLE — it must
 *       pass through MAINTENANCE, so there's always a service record
 *       explaining what was done to resolve the breakdown.</li>
 *   <li>RETIRED → nothing. Terminal state.</li>
 * </ul>
 */
public enum VehicleStatus {
    AVAILABLE,
    ON_TRIP,
    MAINTENANCE,
    BREAKDOWN,
    RETIRED;

    private static final Map<VehicleStatus, Set<VehicleStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(VehicleStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(AVAILABLE, EnumSet.of(ON_TRIP, MAINTENANCE, BREAKDOWN, RETIRED));
        ALLOWED_TRANSITIONS.put(ON_TRIP, EnumSet.of(AVAILABLE, BREAKDOWN));
        ALLOWED_TRANSITIONS.put(MAINTENANCE, EnumSet.of(AVAILABLE, BREAKDOWN, RETIRED));
        ALLOWED_TRANSITIONS.put(BREAKDOWN, EnumSet.of(MAINTENANCE, RETIRED));
        ALLOWED_TRANSITIONS.put(RETIRED, EnumSet.noneOf(VehicleStatus.class));
    }

    public boolean canTransitionTo(VehicleStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
