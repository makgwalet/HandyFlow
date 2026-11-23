package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Rules for moving animals and groups. Before these, a movement record only wrote a history line (the animal stayed in its old
 * paddock), and the target paddock was never checked, so an animal could be "moved" into another farm's or another
 * tenant's production area.
 * <p>
 * Only AREA_TRANSFER changes where an animal is. FARM_TRANSFER, SALE and TRANSFER_OUT are still recorded as history only
 * (they change a farm, status and count together, which comes with the sales and transfers work).
 */
public final class AgMovementRules {

    public static final String AREA_TRANSFER = "AREA_TRANSFER";
    public static final Set<String> TYPES = Set.of(AREA_TRANSFER, "FARM_TRANSFER", "SALE", "TRANSFER_OUT");

    private AgMovementRules() {}

    public static String normaliseType(String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("movementType is required");
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) throw new IllegalArgumentException("movementType must be one of AREA_TRANSFER, FARM_TRANSFER, SALE, TRANSFER_OUT");
        return t;
    }

    public static boolean isAreaTransfer(String normalisedType) {
        return AREA_TRANSFER.equals(normalisedType);
    }

    /** The production area must be on the same farm as the animal or group, and not retired. Resting and quarantine areas are allowed. */
    public static void requireUsableArea(UUID areaFarmId, String areaStatus, UUID targetFarmId) {
        if (areaFarmId == null || !areaFarmId.equals(targetFarmId))
            throw new IllegalArgumentException("That production area is on a different farm. Use a farm transfer to move between farms.");
        if ("INACTIVE".equals(areaStatus))
            throw new IllegalArgumentException("That production area is inactive");
    }

    public static void requireDifferentArea(UUID from, UUID to) {
        if (to == null) throw new IllegalArgumentException("Choose the production area to move to");
        if (to.equals(from)) throw new IllegalArgumentException("It is already in that production area");
    }

    /** A group move names how many head moved: at least one and no more than the group has. Empty means the whole group. */
    public static void requireCountWithinGroup(Integer countMoved, int currentCount) {
        if (countMoved == null) return;
        if (countMoved < 1) throw new IllegalArgumentException("Count moved must be at least 1");
        if (countMoved > currentCount) throw new IllegalArgumentException("Count moved (" + countMoved + ") is more than the group has (" + currentCount + ")");
    }

    /** True when the whole group moves, so the group's own area can change. A part move is recorded but the group stays where it is. */
    public static boolean movesWholeGroup(Integer countMoved, int currentCount) {
        return countMoved == null || countMoved == currentCount;
    }
}
