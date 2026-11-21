package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.security.domain.model.ShiftStatus;

/** Which shifts may be cancelled. Pure. */
final class ShiftCancelRules {

    private ShiftCancelRules() {}

    /** Only a shift that has not started. A running or finished shift is a record of what happened. */
    static boolean cancellable(ShiftStatus status) {
        return status == ShiftStatus.SCHEDULED;
    }
}
