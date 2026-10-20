package za.co.handyflow.platform.fleet.domain.model;

/** Illegal lifecycle change. An IllegalStateException subclass, so it maps to HTTP 409 like InvalidVehicleStatusTransitionException. */
public class InvalidLifecycleTransitionException extends IllegalStateException {

    public InvalidLifecycleTransitionException(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        super("Cannot change vehicle lifecycle from " + from + " to " + to
                + ". Allowed next states are validated by VehicleLifecycleStatus.canTransitionTo().");
    }
}