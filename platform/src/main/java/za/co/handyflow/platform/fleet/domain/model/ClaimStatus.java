package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Insurance claim progress for an accident. Re-saving the same status (to edit details) is always allowed. */
public enum ClaimStatus {
    NOT_CLAIMED, SUBMITTED, ASSESSING, APPROVED, REJECTED, SETTLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = new EnumMap<>(ClaimStatus.class);

    static {
        ALLOWED.put(NOT_CLAIMED, EnumSet.of(SUBMITTED));
        ALLOWED.put(SUBMITTED, EnumSet.of(ASSESSING, APPROVED, REJECTED, NOT_CLAIMED));   // NOT_CLAIMED = withdrawn
        ALLOWED.put(ASSESSING, EnumSet.of(APPROVED, REJECTED, NOT_CLAIMED));
        ALLOWED.put(APPROVED, EnumSet.of(SETTLED, REJECTED));
        ALLOWED.put(REJECTED, EnumSet.of(SUBMITTED, NOT_CLAIMED));                       // SUBMITTED = appeal / resubmit
        ALLOWED.put(SETTLED, EnumSet.noneOf(ClaimStatus.class));
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return this == target || ALLOWED.get(this).contains(target);
    }

    /** A claim that has been lodged but not yet resolved one way or the other. */
    public boolean isUnresolved() {
        return this == SUBMITTED || this == ASSESSING || this == APPROVED;
    }
}