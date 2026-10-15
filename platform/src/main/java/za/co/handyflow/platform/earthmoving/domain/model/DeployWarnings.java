package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What to tell the person deploying a machine. These are warnings only: nothing here stops a deployment. Safety stops
 * (an open critical incident, a breakdown, an unsafe inspection) are enforced elsewhere and are not repeated here.
 */
public final class DeployWarnings {
    private DeployWarnings() {}

    /** An inspection counts for this long. */
    public static final Duration INSPECTION_VALID_FOR = Duration.ofHours(24);

    public static List<String> compute(List<String> overduePlans, boolean dueForService, Instant lastInspectionAt, String lastInspectionResult, Instant now) {
        List<String> out = new ArrayList<>();
        for (String plan : overduePlans) out.add("Service overdue: " + plan + ".");
        if (overduePlans.isEmpty() && dueForService) out.add("This machine has reached its service interval.");
        if (lastInspectionAt == null) {
            out.add("This machine has never been inspected.");
        } else if (Duration.between(lastInspectionAt, now).compareTo(INSPECTION_VALID_FOR) > 0) {
            out.add("No inspection in the last 24 hours.");
        } else if (InspectionRules.DEFECTS.equals(lastInspectionResult)) {
            out.add("The last inspection found defects that are still being tracked.");
        }
        return out;
    }
}
