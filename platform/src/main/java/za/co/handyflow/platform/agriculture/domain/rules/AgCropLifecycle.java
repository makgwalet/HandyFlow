package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;

/**
 * Lifecycle rules for a crop cycle that used to be enforced only by the web UI. The server previously accepted
 * mark-failed and abandon in ANY status (including on an already harvested cycle) and accepted inputs, scouting and
 * harvests on cycles that had failed or had not been planted yet.
 * <p>
 * Statuses: PLANNED, PLANTED, GROWING, HARVESTING, HARVESTED, FAILED, ABANDONED. All violations are
 * {@link IllegalStateException} (HTTP 409) or {@link IllegalArgumentException} (HTTP 400) with a readable message,
 * which is how every other rule in this module is reported.
 */
public final class AgCropLifecycle {

    private AgCropLifecycle() {}

    public enum RecordKind { INPUT, SCOUTING, HARVEST }

    public static boolean isTerminal(String status) {
        return "HARVESTED".equals(status) || "FAILED".equals(status) || "ABANDONED".equals(status);
    }

    private static boolean isInTheGround(String status) {
        return "PLANTED".equals(status) || "GROWING".equals(status) || "HARVESTING".equals(status);
    }

    /** A crop can only fail once it is in the ground. An unplanted cycle that will not go ahead is abandoned instead. */
    public static void requireCanFail(String status) {
        if (!isInTheGround(status)) {
            throw new IllegalStateException("cannot mark failed from status " + status
                    + ": only a planted, growing or harvesting cycle can fail");
        }
    }

    public static void requireCanAbandon(String status) {
        if (isTerminal(status)) {
            throw new IllegalStateException("cannot abandon a cycle that is already " + status);
        }
    }

    /** Inputs and scouting stop once a cycle failed or was abandoned; a harvest also needs something planted. */
    public static void requireAcceptsRecords(RecordKind kind, String status) {
        if ("FAILED".equals(status) || "ABANDONED".equals(status)) {
            String article = "ABANDONED".equals(status) ? "an" : "a";
            throw new IllegalStateException("cannot record " + noun(kind) + " on " + article + " " + status.toLowerCase() + " crop cycle");
        }
        if (kind == RecordKind.HARVEST && "PLANNED".equals(status)) {
            throw new IllegalStateException("cannot record a harvest on a crop cycle that has not been planted yet");
        }
    }

    public static void requireHarvestNotBeforePlanting(LocalDate plantingDate, LocalDate expectedHarvestDate) {
        if (plantingDate != null && expectedHarvestDate != null && expectedHarvestDate.isBefore(plantingDate)) {
            throw new IllegalArgumentException("expectedHarvestDate must not be before plantingDate");
        }
    }

    private static String noun(RecordKind kind) {
        return switch (kind) {
            case INPUT -> "an input";
            case SCOUTING -> "scouting";
            case HARVEST -> "a harvest";
        };
    }
}
