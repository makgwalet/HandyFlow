package za.co.handyflow.platform.earthmoving.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle state of a piece of heavy equipment, with explicit legal
 * transitions.
 * <p>
 * WHY an enum instead of a String, like the original code had? A String
 * status field accepts literally anything — a typo like "DEPLOYD" compiles
 * fine and fails at runtime, deep inside a switch statement's default case,
 * usually in production. An enum turns that into a compile error. It also
 * gives us one place — right here — to define which transitions are even
 * legal, instead of that logic being implicit in "whichever code path
 * happens to call whichever setter".
 * <p>
 * WHY explicit transitions at all? The original code let you call
 * {@code asset.retire()} on a machine that's currently DEPLOYED on a client
 * site, or {@code asset.deploy()} on one that's already RETIRED — nothing
 * stopped it. For physical equipment that real people rely on for safety
 * and billing, "the software let me put a scrapped bulldozer back on a job
 * site" is not a hypothetical bug, it's a liability. This model makes the
 * illegal transition impossible instead of trusting every call site to
 * remember the rules.
 */
public enum AssetStatus {
    AVAILABLE,
    DEPLOYED,
    MAINTENANCE,
    BREAKDOWN,
    HIRED_OUT,
    RETIRED;

    private static final Map<AssetStatus, Set<AssetStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(AssetStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(AVAILABLE, EnumSet.of(DEPLOYED, MAINTENANCE, HIRED_OUT, RETIRED));
        ALLOWED_TRANSITIONS.put(DEPLOYED, EnumSet.of(AVAILABLE, BREAKDOWN, MAINTENANCE));
        ALLOWED_TRANSITIONS.put(MAINTENANCE, EnumSet.of(AVAILABLE, BREAKDOWN, RETIRED));
        ALLOWED_TRANSITIONS.put(BREAKDOWN, EnumSet.of(MAINTENANCE, RETIRED));
        ALLOWED_TRANSITIONS.put(HIRED_OUT, EnumSet.of(AVAILABLE, BREAKDOWN));
        ALLOWED_TRANSITIONS.put(RETIRED, EnumSet.noneOf(AssetStatus.class)); // terminal state
    }

    public boolean canTransitionTo(AssetStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}