package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Checks on the things a record points at (farm, area, enterprise, parents) and on what a farm still holds before it can be
 * deactivated or deleted. Pure: the services look the rows up and hand the facts to these methods.
 */
public final class AgReferenceRules {

    private AgReferenceRules() {}

    /** A new record cannot be put on an inactive farm. */
    public static void requireFarmUsable(String farmStatus, String farmName) {
        if ("INACTIVE".equals(farmStatus)) throw new IllegalStateException("Farm " + farmName + " is inactive. Reactivate it before adding to it");
    }

    /** {@code what} is e.g. "enterprise Dairy". */
    public static void requireOnFarm(String what, UUID ownerFarmId, UUID farmId) {
        if (!Objects.equals(ownerFarmId, farmId)) throw new IllegalArgumentException(what + " does not belong to this farm");
    }

    /**
     * A sire must be a male, a dam a female, of the same species, born before the animal, and neither can be the animal itself.
     * Unknown details (sex aside) are not held against the parent.
     */
    public static void requireParent(String role, UUID parentId, UUID selfId, String parentSex, UUID parentSpeciesId, UUID speciesId,
                                     LocalDate parentBorn, LocalDate childBorn) {
        if (parentId == null) return;
        if (parentId.equals(selfId)) throw new IllegalArgumentException("An animal cannot be its own " + role);
        String needed = "sire".equals(role) ? "MALE" : "FEMALE";
        if (!needed.equals(parentSex)) throw new IllegalArgumentException("The " + role + " must be " + (needed.equals("MALE") ? "a male" : "a female") + " animal");
        if (speciesId != null && !speciesId.equals(parentSpeciesId)) throw new IllegalArgumentException("The " + role + " is a different species");
        if (parentBorn != null && childBorn != null && !parentBorn.isBefore(childBorn))
            throw new IllegalArgumentException("The " + role + " must be born before the animal");
    }

    /** The sire and dam cannot be the same animal. */
    public static void requireDifferentParents(UUID sireId, UUID damId) {
        if (sireId != null && sireId.equals(damId)) throw new IllegalArgumentException("The sire and the dam cannot be the same animal");
    }

    /** What stops a farm being deactivated or deleted, e.g. "12 animals, 2 groups"; null when it holds nothing live. */
    public static String farmHoldings(long animals, long groups, long cropCycles, long stockItems) {
        List<String> parts = new ArrayList<>();
        if (animals > 0) parts.add(animals + (animals == 1 ? " animal" : " animals") + " on the farm");
        if (groups > 0) parts.add(groups + (groups == 1 ? " active group" : " active groups"));
        if (cropCycles > 0) parts.add(cropCycles + (cropCycles == 1 ? " crop cycle in progress" : " crop cycles in progress"));
        if (stockItems > 0) parts.add(stockItems + (stockItems == 1 ? " stock item with stock on hand" : " stock items with stock on hand"));
        if (parts.isEmpty()) return null;
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    public static void requireNoHoldings(String farmName, String action, long animals, long groups, long cropCycles, long stockItems) {
        String holdings = farmHoldings(animals, groups, cropCycles, stockItems);
        if (holdings != null) throw new IllegalStateException("Farm " + farmName + " cannot be " + action + ": it still has " + holdings + ". Sell, close, harvest or move them first");
    }
}
