package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.util.UUID;

/**
 * Rules for booking site resources into timetable slots. Pure functions: each returns a plain-words problem or null. Booking a resource says
 * nothing about whether it satisfies any standard.
 */
public final class ResourceBookingRules {

    public static final String BOOKED = "BOOKED";
    public static final String RELEASED = "RELEASED";
    public static final String RESOURCE = "RESOURCE";
    public static final int MIN_REASON = 10;

    private ResourceBookingRules() {
    }

    /** Why a resource cannot be booked into a slot at all (clashes are separate), or null. */
    public static String bookProblem(String resourceStatus, UUID resourceSiteId, UUID cohortSiteId, String slotStatus, Integer quantity, int resourceQuantity) {
        if (!"SCHEDULED".equals(slotStatus)) {
            return "A cancelled slot cannot have resources booked";
        }
        if (!ResourceRules.IN_SERVICE.equals(resourceStatus)) {
            return "That resource is not in service";
        }
        if (resourceSiteId == null || !resourceSiteId.equals(cohortSiteId)) {
            return "That resource is held at a different site than the cohort";
        }
        if (quantity == null || quantity < 1) {
            return "Book at least one";
        }
        if (quantity > resourceQuantity) {
            return "The site holds " + resourceQuantity + " of that resource, so " + quantity + " cannot be booked";
        }
        return null;
    }

    /**
     * What is double-booked, or null. A shareable resource is never a clash. Otherwise the quantity already booked at overlapping times plus this
     * booking may not be more than the quantity the site holds.
     */
    public static String clash(String resourceName, boolean shareable, int resourceQuantity, int alreadyBooked, int quantity) {
        if (shareable || alreadyBooked + quantity <= resourceQuantity) {
            return null;
        }
        int free = Math.max(0, resourceQuantity - alreadyBooked);
        return resourceName + ": " + alreadyBooked + " of " + resourceQuantity + " already booked at that time, so only " + free + " left and " + quantity + " asked for";
    }

    public static String releaseProblem(String reason) {
        return reason != null && reason.trim().length() >= MIN_REASON ? null : "Give a reason of at least " + MIN_REASON + " characters";
    }
}
