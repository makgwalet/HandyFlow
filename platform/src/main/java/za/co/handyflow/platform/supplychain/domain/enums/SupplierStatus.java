package za.co.handyflow.platform.supplychain.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Supplier lifecycle. Only ACTIVE suppliers can receive new purchase orders
 * (ScSupplier.isOrderable()). Status changes go through SupplierLifecycleService —
 * never by editing the supplier — so every change has a reason, an actor and an audit event.
 * <pre>
 * PENDING_APPROVAL -> ACTIVE | INACTIVE
 * ACTIVE           -> SUSPENDED | BLACKLISTED | INACTIVE
 * SUSPENDED        -> ACTIVE | BLACKLISTED | INACTIVE     (temporary hold, e.g. expired documents)
 * BLACKLISTED      -> ACTIVE | INACTIVE                   (hard block; lifting it is a controlled action)
 * INACTIVE         -> PENDING_APPROVAL | ACTIVE           (re-onboarding)
 * </pre>
 */
public enum SupplierStatus {
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    BLACKLISTED,
    INACTIVE;

    public Set<SupplierStatus> allowedNext() {
        return switch (this) {
            case PENDING_APPROVAL -> EnumSet.of(ACTIVE, INACTIVE);
            case ACTIVE -> EnumSet.of(SUSPENDED, BLACKLISTED, INACTIVE);
            case SUSPENDED -> EnumSet.of(ACTIVE, BLACKLISTED, INACTIVE);
            case BLACKLISTED -> EnumSet.of(ACTIVE, INACTIVE);
            case INACTIVE -> EnumSet.of(PENDING_APPROVAL, ACTIVE);
        };
    }

    public boolean canTransitionTo(SupplierStatus target) {
        return allowedNext().contains(target);
    }
}
