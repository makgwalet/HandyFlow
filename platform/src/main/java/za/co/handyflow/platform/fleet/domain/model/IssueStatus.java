package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a fleet issue, with explicit legal transitions (same pattern as
 * {@link VehicleStatus}). RESOLVED and DISMISSED can be reopened — a "fixed" issue that
 * recurs should come back to OPEN rather than spawn a duplicate.
 */
public enum IssueStatus {
    OPEN, IN_PROGRESS, RESOLVED, DISMISSED;

    /** Statuses that count as "still needs attention". */
    public static final List<IssueStatus> ACTIVE = List.of(OPEN, IN_PROGRESS);

    private static final Map<IssueStatus, Set<IssueStatus>> ALLOWED = new EnumMap<>(IssueStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(IN_PROGRESS, RESOLVED, DISMISSED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(OPEN, RESOLVED, DISMISSED));
        ALLOWED.put(RESOLVED, EnumSet.of(OPEN));
        ALLOWED.put(DISMISSED, EnumSet.of(OPEN));
    }

    public boolean canTransitionTo(IssueStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}