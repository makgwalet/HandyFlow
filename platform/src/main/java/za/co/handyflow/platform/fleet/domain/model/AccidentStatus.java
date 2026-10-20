package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an accident case, with explicit legal transitions (same pattern as
 * {@link VehicleStatus}). CLOSED can be reopened (to UNDER_ASSESSMENT); WRITTEN_OFF is
 * terminal — it is the case outcome "vehicle declared a total loss", and the disposal
 * workflow in the lifecycle chunk takes over from there.
 */
public enum AccidentStatus {
    REPORTED, UNDER_ASSESSMENT, IN_REPAIR, CLOSED, WRITTEN_OFF;

    private static final Map<AccidentStatus, Set<AccidentStatus>> ALLOWED = new EnumMap<>(AccidentStatus.class);

    static {
        ALLOWED.put(REPORTED, EnumSet.of(UNDER_ASSESSMENT, IN_REPAIR, CLOSED, WRITTEN_OFF));
        ALLOWED.put(UNDER_ASSESSMENT, EnumSet.of(IN_REPAIR, CLOSED, WRITTEN_OFF));
        ALLOWED.put(IN_REPAIR, EnumSet.of(UNDER_ASSESSMENT, CLOSED, WRITTEN_OFF));
        ALLOWED.put(CLOSED, EnumSet.of(UNDER_ASSESSMENT));
        ALLOWED.put(WRITTEN_OFF, EnumSet.noneOf(AccidentStatus.class));
    }

    public boolean canTransitionTo(AccidentStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}