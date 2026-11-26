package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * The site resource register: what equipment and similar resources a delivery site holds, and the state each is in. It is a register, not a
 * booking system, and having a resource listed says nothing about whether it satisfies any standard.
 */
public final class ResourceRules {

    public static final String IN_SERVICE = "IN_SERVICE";
    public static final String UNDER_REPAIR = "UNDER_REPAIR";
    public static final String OUT_OF_SERVICE = "OUT_OF_SERVICE";
    public static final String RETIRED = "RETIRED";
    public static final Set<String> TYPES = Set.of("EQUIPMENT", "VEHICLE", "PRACTICAL_KIT", "SIMULATOR", "IT_EQUIPMENT", "CONSUMABLES", "OTHER");
    public static final Set<String> STATUSES = Set.of(IN_SERVICE, UNDER_REPAIR, OUT_OF_SERVICE, RETIRED);
    public static final int MIN_REASON = 10;
    public static final int MAX_QUANTITY = 100000;

    private static final Map<String, Set<String>> NEXT = Map.of(
            IN_SERVICE, Set.of(UNDER_REPAIR, OUT_OF_SERVICE, RETIRED),
            UNDER_REPAIR, Set.of(IN_SERVICE, OUT_OF_SERVICE, RETIRED),
            OUT_OF_SERVICE, Set.of(IN_SERVICE, UNDER_REPAIR, RETIRED),
            RETIRED, Set.of());

    private ResourceRules() {
    }

    public static Set<String> nextStatuses(String status) {
        return NEXT.getOrDefault(status, Set.of());
    }

    public static String problem(String name, String type, Integer quantity) {
        if (name == null || name.trim().length() < 2) {
            return "Give the resource a name";
        }
        if (type == null || !TYPES.contains(type)) {
            return "Choose the type of resource";
        }
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            return "Quantity must be at least 1 and no more than " + MAX_QUANTITY;
        }
        return null;
    }

    /** A change of state needs a reason, a day that is not in the future, and a move the register allows. */
    public static String statusProblem(String from, String to, String reason, LocalDate on, LocalDate today) {
        if (to == null || !STATUSES.contains(to)) {
            return "Choose a state";
        }
        if (!nextStatuses(from).contains(to)) {
            return RETIRED.equals(from) ? "A retired resource stays retired" : "That change of state is not allowed";
        }
        if (reason == null || reason.trim().length() < MIN_REASON) {
            return "Give a reason of at least " + MIN_REASON + " characters";
        }
        if (on == null) {
            return "Give the day it changed";
        }
        if (on.isAfter(today)) {
            return "The change cannot be dated in the future";
        }
        return null;
    }

    public static String moveProblem(String status, boolean sameSite, boolean targetSiteActive, boolean venueBelongsToTarget, String reason) {
        if (RETIRED.equals(status)) {
            return "A retired resource cannot be moved";
        }
        if (sameSite) {
            return "It is already at that site";
        }
        if (!targetSiteActive) {
            return "That site is not active";
        }
        if (!venueBelongsToTarget) {
            return "That venue is not at the site";
        }
        return reason == null || reason.trim().length() < MIN_REASON ? "Give a reason of at least " + MIN_REASON + " characters" : null;
    }

    public static String label(String status) {
        return switch (status) {
            case IN_SERVICE -> "In service";
            case UNDER_REPAIR -> "Under repair";
            case OUT_OF_SERVICE -> "Out of service";
            case RETIRED -> "Retired";
            default -> status;
        };
    }
}
