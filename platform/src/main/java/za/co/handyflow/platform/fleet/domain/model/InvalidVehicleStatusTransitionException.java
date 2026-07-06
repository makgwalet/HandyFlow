package za.co.handyflow.platform.fleet.domain.model;

/**
 * Thrown when code tries to move a vehicle to a status that isn't reachable
 * from its current one. A distinct type (not a bare IllegalStateException)
 * so the global exception handler maps it to a precise 409, and tests can
 * assert on it specifically. Mirrors earthmoving's
 * InvalidAssetStatusTransitionException exactly.
 */
public class InvalidVehicleStatusTransitionException extends IllegalStateException {

    public InvalidVehicleStatusTransitionException(VehicleStatus from, VehicleStatus to) {
        super("Cannot change vehicle status from " + from + " to " + to
                + ". Allowed next states from " + from + " are validated by VehicleStatus.canTransitionTo().");
    }
}
