package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.Set;

/**
 * When an engagement's work is frozen. From the moment the report goes for sign-off until it is rejected or returned,
 * and for good once approved, unless the Head of Internal Audit has reopened it for amendment (an open amendment).
 */
public final class ReportLock {
    private ReportLock() {}

    private static final Set<String> FROZEN = Set.of("SUBMITTED", "IN_PROGRESS", "RESUBMITTED", "APPROVED");

    /** latestStatus is the status of the newest sign-off request for the report, or null if none was ever submitted. */
    public static boolean isLocked(String latestStatus, boolean openAmendment) {
        return latestStatus != null && FROZEN.contains(latestStatus) && !openAmendment;
    }
}
