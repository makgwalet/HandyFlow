package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evidence needed to release a machine from maintenance or breakdown: a work order completed since it went out of
 * service, and a different person doing the release than the one who finished the work. Either can be overridden with
 * a written reason, which is audited. This is the minimum; it is not configurable.
 */
public final class ReleaseRules {
    private ReleaseRules() {}

    public static final String NO_WORK = "No work order has been completed on this machine since it went out of service.";
    public static final String SAME_PERSON = "You completed the latest work order yourself; a different person should approve the release.";

    /** True when the work order was completed after the machine went out of service (or the time it went out is unknown). */
    public static boolean isEvidence(Instant wentOutOfService, Instant completedAt) {
        return completedAt != null && (wentOutOfService == null || !completedAt.isBefore(wentOutOfService));
    }

    public static List<String> problems(Instant wentOutOfService, Instant completedAt, UUID completedBy, UUID releasedBy) {
        List<String> out = new ArrayList<>();
        if (!isEvidence(wentOutOfService, completedAt)) {
            out.add(NO_WORK);
        } else if (completedBy != null && completedBy.equals(releasedBy)) {
            out.add(SAME_PERSON);
        }
        return out;
    }
}
