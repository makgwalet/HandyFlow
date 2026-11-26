package za.co.handyflow.platform.trainingprovider.domain.rules;

/**
 * Resources a programme version lists as needed. The list is the provider's own, with a note of where each need comes from. It holds no
 * standard and no regulatory figure, and a need being met never shows that the programme meets any standard.
 */
public final class ProgrammeNeedsRules {

    public static final int MIN_NOTE = 10;
    public static final String ENOUGH = "ENOUGH";
    public static final String SHORT = "SHORT";
    public static final String NONE_HELD = "NONE_HELD";

    private ProgrammeNeedsRules() {
    }

    public static String problem(String type, String description, String matchText, Integer quantity, Integer perLearners, String sourceNote) {
        if (type == null || !ResourceRules.TYPES.contains(type)) {
            return "Choose the type of resource";
        }
        if (description == null || description.trim().length() < 2) {
            return "Say what is needed";
        }
        if (matchText != null && !matchText.isBlank() && matchText.trim().length() < 2) {
            return "The words to look for in the resource name need at least two characters";
        }
        if (quantity == null || quantity < 1 || quantity > ResourceRules.MAX_QUANTITY) {
            return "The quantity must be at least 1";
        }
        if (perLearners != null && (perLearners < 1 || perLearners > ResourceRules.MAX_QUANTITY)) {
            return "Learners per set must be at least 1";
        }
        if (sourceNote == null || sourceNote.trim().length() < MIN_NOTE) {
            return "Say where the need comes from, in at least " + MIN_NOTE + " characters";
        }
        return null;
    }

    public static String withdrawProblem(String reason) {
        return reason != null && reason.trim().length() >= MIN_NOTE ? null : "Give a reason of at least " + MIN_NOTE + " characters";
    }

    /** How many are listed as needed for a cohort of this size. With learners-per-set, the quantity is needed for each started group of that size. */
    public static int required(int quantity, Integer perLearners, long learners) {
        if (perLearners == null) {
            return quantity;
        }
        long groups = Math.max(1, (learners + perLearners - 1) / perLearners);
        return (int) Math.min(Integer.MAX_VALUE, groups * quantity);
    }

    /** True when the resource's name contains the words to look for, ignoring case. A blank match means every resource of the type counts. */
    public static boolean matches(String resourceName, String matchText) {
        if (matchText == null || matchText.isBlank()) {
            return true;
        }
        return resourceName != null && resourceName.toLowerCase().contains(matchText.trim().toLowerCase());
    }

    public static String readiness(int required, int held) {
        if (held <= 0) {
            return NONE_HELD;
        }
        return held >= required ? ENOUGH : SHORT;
    }

    /** Plain words for a reader. Always a prompt to check, never a finding about the programme. */
    public static String note(String status, int required, int held) {
        return switch (status) {
            case ENOUGH -> "The site register lists " + held + " in service; the programme lists " + required;
            case SHORT -> "The site register lists " + held + " in service; the programme lists " + required + ". Check before the cohort starts";
            default -> "The site register lists none in service; the programme lists " + required + ". Check before the cohort starts";
        };
    }

    /** Plain words for one session. Always a prompt to check the booking, never a finding. */
    public static String slotNote(String status, int required, int booked) {
        return switch (status) {
            case ENOUGH -> "Booked for this session: " + booked + "; the programme lists " + required;
            case SHORT -> "Booked for this session: " + booked + "; the programme lists " + required + ". Check the bookings before the session";
            default -> "Nothing is booked for this session; the programme lists " + required + ". Check the bookings before the session";
        };
    }
}
