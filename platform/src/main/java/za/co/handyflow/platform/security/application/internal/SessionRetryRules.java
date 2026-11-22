package za.co.handyflow.platform.security.application.internal;

import java.util.UUID;

/**
 * When the phone repeats a clock-in, clock-out or item return because the answer never reached it, the repeat should
 * get the first result back instead of an error. Pure logic.
 */
final class SessionRetryRules {

    enum ReturnOutcome { RETURN_NOW, REPLAY, CONFLICT }

    private SessionRetryRules() {}

    /** A clock-in repeat: the session already open on this device is this same guard's. */
    static boolean isOpenReplay(UUID openSessionGuardId, UUID requestingGuardId) {
        return openSessionGuardId != null && openSessionGuardId.equals(requestingGuardId);
    }

    /** A clock-out repeat: the session is already closed (ownership is checked before this is asked). */
    static boolean isCloseReplay(boolean alreadyClosed) {
        return alreadyClosed;
    }

    /**
     * An item return: still out means do it. Already returned with the same condition is a repeat of the same
     * request (answer with what is stored). Already returned with a different condition is a real conflict.
     */
    static ReturnOutcome returnOutcome(boolean stillCheckedOut, String storedCondition, String requestedCondition) {
        if (stillCheckedOut) return ReturnOutcome.RETURN_NOW;
        if (storedCondition != null && storedCondition.equals(requestedCondition)) return ReturnOutcome.REPLAY;
        return ReturnOutcome.CONFLICT;
    }
}
