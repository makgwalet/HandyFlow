package za.co.handyflow.platform.shared;

import java.util.UUID;

/**
 * The separation-of-duties rule, in one place and pure so it is tested without Spring (IAM Phase 1, item 4).
 * <p>
 * Whoever did the earlier step of a transaction does not do the checking step. The one exception is a business where
 * nobody else holds the permission for the checking step: blocking it would stop the business working, so the step is
 * allowed, and it must be logged (see {@link SeparationOfDutiesChecker}) so the owner can show it to an accountant.
 * That exception switches itself off as soon as a second person is given the permission.
 */
public final class SeparationOfDuties {

    private SeparationOfDuties() {}

    /** Null when {@code actor} may do the step, otherwise {@code message} with how many other people could do it. */
    public static String problem(String message, UUID earlier, UUID actor, long otherHolders) {
        if (earlier == null || actor == null || !earlier.equals(actor)) return null;
        if (otherHolders <= 0) return null;
        return message + (otherHolders == 1 ? " (one other person can)" : " (" + otherHolders + " other people can)");
    }

    /** True when the step is allowed only because nobody else can do it; the caller logs it. */
    public static boolean soleException(UUID earlier, UUID actor, long otherHolders) {
        return earlier != null && earlier.equals(actor) && otherHolders <= 0;
    }
}
