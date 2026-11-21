package za.co.handyflow.platform.security.application.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks on the guard proposed to take over a shift, at approval time.
 * BLOCKING failures can never be overridden (an unregistered or suspended
 * guard may not lawfully be deployed). A double-booking is a warning the
 * supervisor can approve with, and it is recorded in the swap's notes.
 */
final class SwapChecks {

    record Result(List<String> blocking, List<String> warnings) {
        boolean blocked() { return !blocking.isEmpty(); }
        boolean allPassed() { return blocking.isEmpty() && warnings.isEmpty(); }
    }

    private SwapChecks() {}

    /** @param status the guard's status text, only used in the message; null when the guard was not found */
    static Result evaluate(boolean guardFound, boolean schedulable, String status,
                           LocalDate psiraExpiry, LocalDate shiftDate, boolean overlaps) {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!guardFound) {
            blocking.add("Proposed guard not found or deleted.");
            return new Result(blocking, warnings);
        }
        if (!schedulable) {
            blocking.add("Proposed guard is " + status + " and cannot be scheduled.");
        }
        if (psiraExpiry != null && psiraExpiry.isBefore(shiftDate)) {
            blocking.add("Proposed guard's PSiRA registration expired on " + psiraExpiry
                    + ". Renew it before assigning this shift.");
        }
        if (overlaps) {
            warnings.add("Proposed guard has an overlapping shift.");
        }
        return new Result(blocking, warnings);
    }
}
