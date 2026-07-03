package za.co.handyflow.platform.earthmoving.domain.model;

/**
 * Thrown when code tries to move an asset to a status that isn't reachable
 * from its current one (e.g. RETIRED -> DEPLOYED). Deliberately a distinct
 * type rather than a generic IllegalStateException so the global exception
 * handler can map it to HTTP 409 Conflict with a precise message, and so
 * tests can assert on it specifically instead of string-matching a message.
 */
public class InvalidAssetStatusTransitionException extends IllegalStateException {

    public InvalidAssetStatusTransitionException(AssetStatus from, AssetStatus to) {
        super("Cannot change asset status from " + from + " to " + to
                + ". Allowed next states from " + from + " are validated by AssetStatus.canTransitionTo().");
    }
}