package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * What may happen to an animal or group over its life, and what a change of head count must say about itself.
 * <p>
 * Before this, an animal's status was a free field (a deceased animal could be set back to active, or sold after it died), a
 * deceased or sold animal could still be moved, bred or killed twice, a group's head count could be raised or cut with no
 * reason and no trace, and a sale or transfer-out recorded as movement history left the animal still "active" on the farm.
 * <p>
 * Animal status: ACTIVE becomes SOLD, DECEASED, CULLED or TRANSFERRED_OUT. A sold, culled or transferred-out animal can be put
 * back to ACTIVE (a mistake or a return). A DECEASED animal has a mortality record and stays deceased.
 */
public final class AgLifecycleRules {

    public static final String ACTIVE = "ACTIVE";
    public static final Set<String> LEAVING = Set.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT");
    public static final Set<String> ANIMAL_STATUSES = Set.of("ACTIVE", "SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT");
    private static final Set<String> REINSTATABLE = Set.of("SOLD", "CULLED", "TRANSFERRED_OUT");
    public static final int MIN_REASON = 3;

    private AgLifecycleRules() {}

    /** The statuses an animal in {@code from} may be changed to. */
    public static Set<String> allowedAnimalStatuses(String from) {
        if (ACTIVE.equals(from)) return LEAVING;
        if (from != null && REINSTATABLE.contains(from)) return Set.of(ACTIVE);
        return Set.of();
    }

    public static void requireAnimalStatusChange(String from, String to) {
        if (to == null || !ANIMAL_STATUSES.contains(to))
            throw new IllegalArgumentException("status must be one of ACTIVE, SOLD, DECEASED, CULLED, TRANSFERRED_OUT");
        if (to.equals(from)) throw new IllegalStateException("This animal is already " + label(to));
        if ("DECEASED".equals(from)) throw new IllegalStateException("A deceased animal cannot be changed to " + label(to));
        if (!allowedAnimalStatuses(from).contains(to))
            throw new IllegalStateException("A " + label(from) + " animal cannot be changed to " + label(to) + ". Put it back to active first");
    }

    /** Animals and groups can only be moved, bred, fed from stock, sold or lost while they are still on the farm. */
    public static void requireOnFarm(String status, String what) {
        if (!ACTIVE.equals(status)) throw new IllegalStateException(what + " is " + label(status) + " and can no longer be changed this way");
    }

    /** A reason, trimmed. At least three characters, so "x" or "." is not accepted. */
    public static String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for changing the head count (at least " + MIN_REASON + " characters)");
        return r;
    }

    /** The status an animal takes when a movement of this type is recorded; null when the type does not end its stay. */
    public static String animalStatusForMovement(String movementType) {
        return switch (movementType) {
            case "SALE" -> "SOLD";
            case "TRANSFER_OUT" -> "TRANSFERRED_OUT";
            default -> null;
        };
    }

    /** A group sale or transfer out takes head off the count; with no count given the whole group goes. */
    public static int headsLeaving(Integer countMoved, int currentCount) {
        int n = countMoved == null ? currentCount : countMoved;
        if (n < 1) throw new IllegalArgumentException("Count must be at least 1");
        if (n > currentCount) throw new IllegalArgumentException("Count (" + n + ") is more than the group has (" + currentCount + ")");
        return n;
    }

    /** A whole-group transfer to another farm. Part of a group cannot change farm yet (the destination would need a group of its own). */
    public static void requireWholeGroupForFarmTransfer(Integer countMoved, int currentCount) {
        if (countMoved != null && countMoved != currentCount)
            throw new IllegalArgumentException("Only a whole group can be transferred to another farm. Transfer all " + currentCount + ", or record a sale or transfer out for part of it");
    }

    public static void requireDifferentFarm(java.util.UUID fromFarmId, java.util.UUID toFarmId) {
        if (toFarmId == null) throw new IllegalArgumentException("Choose the farm to transfer to");
        if (toFarmId.equals(fromFarmId)) throw new IllegalArgumentException("It is already on that farm");
    }

    /** A loss: an individually tracked animal is one head; a group cannot lose more than it has. */
    public static void requireMortalityCount(boolean individual, int countLost, int currentGroupCount) {
        if (countLost < 1) throw new IllegalArgumentException("countLost must be positive");
        if (individual) {
            if (countLost != 1) throw new IllegalArgumentException("countLost must be 1 for an individually-tracked animal");
        } else if (countLost > currentGroupCount) {
            throw new IllegalArgumentException("Cannot record " + countLost + " lost: the group has " + currentGroupCount);
        }
    }

    public static void requireNotFuture(LocalDate date, LocalDate today, String what) {
        if (date != null && date.isAfter(today)) throw new IllegalArgumentException(what + " cannot be in the future");
    }

    private static String label(String s) { return s == null ? "unknown" : s.toLowerCase().replace('_', ' '); }
}
