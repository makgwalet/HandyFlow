package za.co.handyflow.platform.billing.domain.model;

import java.util.Set;

public enum SubscriptionStatus {

    PILOT,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED;

    /*
     * WHY switch instead of anonymous classes?
     * Anonymous class bodies inside enums lose access to sibling
     * enum constants — causing "Cannot resolve symbol" errors.
     * A switch expression on the outer enum is clean, readable,
     * and has full access to all constants.
     */
    public Set<SubscriptionStatus> allowedTransitions() {
        return switch (this) {
            case PILOT     -> Set.of(ACTIVE, SUSPENDED, CANCELLED);
            case ACTIVE    -> Set.of(PAST_DUE, SUSPENDED, CANCELLED);
            case PAST_DUE  -> Set.of(ACTIVE, SUSPENDED, CANCELLED);
            case SUSPENDED -> Set.of(ACTIVE, CANCELLED);
            case CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(SubscriptionStatus target) {
        return allowedTransitions().contains(target);
    }
}
