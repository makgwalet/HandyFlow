package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/** When to chase an overdue SAPS report: once it is overdue, then at most once a day. Pure. */
final class SapsChase {

    static final Duration REPEAT = Duration.ofHours(24);

    private SapsChase() {}

    static boolean shouldAlert(Instant now, Instant dueAt, String caseNumber, Instant lastAlertedAt) {
        if (!LostReportRules.overdue(now, dueAt, caseNumber)) return false;
        return lastAlertedAt == null || !lastAlertedAt.plus(REPEAT).isAfter(now);
    }
}
