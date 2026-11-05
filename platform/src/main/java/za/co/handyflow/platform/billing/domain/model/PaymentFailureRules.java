package za.co.handyflow.platform.billing.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * What a business may do after a failed subscription payment (owner decision of 5 Nov 2026). Pure.
 *
 * <pre>
 *  day 0 ........ 7 ............................ 37 ...........
 *  PAST_DUE        |  read-only for paid modules |  restricted
 *  full access     |  (recovery period)          |  data and exports kept
 * </pre>
 * A successful payment restores everything at any point. Authentication, security, billing and data export are
 * available in every stage. Voluntary cancellation is a separate path with its own 30-day read-only transition.
 *
 * REPORT MODE: nothing calls this to block anything yet. Today the scheduler suspends a business after the 7-day grace
 * and every module then answers 402; moving to these stages needs the staged rollout the owner has yet to approve.
 */
public final class PaymentFailureRules {

    public static final int GRACE_DAYS = 7, RECOVERY_DAYS = 30;

    public enum Access { FULL, READ_ONLY, RESTRICTED }

    /** @param graceEndsAt writes to paid modules stop here; @param recoveryEndsAt operational access is restricted here */
    public record Stage(Access access, Instant graceEndsAt, Instant recoveryEndsAt, long daysLeftInStage) {}

    /** Areas that stay available in every stage, so a business can always sign in, pay and take its data. */
    public static final Set<String> ALWAYS_AVAILABLE = Set.of("AUTHENTICATION", "SECURITY", "BILLING", "DATA_EXPORT");

    private PaymentFailureRules() {}

    /** @param pastDueSince when the payment failed; null means no payment failure (then access is full) */
    public static Stage stage(Instant pastDueSince, Instant now) {
        if (pastDueSince == null) return new Stage(Access.FULL, null, null, 0);
        Instant graceEnds = pastDueSince.plus(Duration.ofDays(GRACE_DAYS));
        Instant recoveryEnds = graceEnds.plus(Duration.ofDays(RECOVERY_DAYS));
        if (!now.isAfter(graceEnds)) return new Stage(Access.FULL, graceEnds, recoveryEnds, days(now, graceEnds));
        if (!now.isAfter(recoveryEnds)) return new Stage(Access.READ_ONLY, graceEnds, recoveryEnds, days(now, recoveryEnds));
        return new Stage(Access.RESTRICTED, graceEnds, recoveryEnds, 0);
    }

    /** May a paid module accept a change (create, edit, delete) in this stage? Reading is allowed until RESTRICTED. */
    public static boolean allowsPaidModuleWrite(Access a) { return a == Access.FULL; }

    public static boolean allowsPaidModuleRead(Access a) { return a != Access.RESTRICTED; }

    public static boolean alwaysAvailable(String area) { return area != null && ALWAYS_AVAILABLE.contains(area); }

    private static long days(Instant from, Instant to) { return Math.max(0, Duration.between(from, to).toDays()); }
}
