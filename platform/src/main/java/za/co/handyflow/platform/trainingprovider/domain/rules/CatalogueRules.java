package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for the qualification and programme catalogue. No qualification,
 * credit or registration data is built in: the provider records what the
 * authority publishes, and these rules keep the three kinds of training
 * apart (accredited, non-accredited, workplace-based).
 */
public final class CatalogueRules {

    private CatalogueRules() {
    }

    public static final String ACCREDITED = "ACCREDITED";
    public static final String NON_ACCREDITED = "NON_ACCREDITED";
    public static final String WORKPLACE_BASED = "WORKPLACE_BASED";

    public static final Set<String> QUALIFICATION_TYPES = Set.of("QUALIFICATION", "PART_QUALIFICATION", "SKILLS_PROGRAMME",
            "UNIT_STANDARD", "PROFESSIONAL_DEVELOPMENT", "OTHER");
    public static final Set<String> REGISTRATION_STATUSES = Set.of("REGISTERED", "RE_REGISTERED", "NOT_YET_REGISTERED", "LAPSED", "UNKNOWN");
    public static final Set<String> DELIVERY_MODES = Set.of("CLASSROOM", "PRACTICAL", "ONLINE", "BLENDED", "WORKPLACE");
    public static final Set<String> MODULE_TYPES = Set.of("KNOWLEDGE", "PRACTICAL", "WORKPLACE");

    private static final Map<String, Set<String>> TYPES_BY_ACTIVITY = Map.of(
            ACCREDITED, Set.of("QUALIFICATION", "PART_QUALIFICATION", "SKILLS_PROGRAMME", "UNIT_STANDARD_PROGRAMME"),
            NON_ACCREDITED, Set.of("NON_ACCREDITED_COURSE", "PROFESSIONAL_DEVELOPMENT", "CUSTOM_CORPORATE"),
            WORKPLACE_BASED, Set.of("QUALIFICATION", "PART_QUALIFICATION", "SKILLS_PROGRAMME", "UNIT_STANDARD_PROGRAMME", "CUSTOM_CORPORATE"));

    /** Why a programme's basic shape is wrong, or null. A non-accredited course can never be filed as a qualification. */
    public static String programmeProblem(String activity, String programmeType, String deliveryMode, UUID qualificationId) {
        if (activity == null || !TYPES_BY_ACTIVITY.containsKey(activity)) {
            return "Training activity must be ACCREDITED, NON_ACCREDITED or WORKPLACE_BASED";
        }
        if (programmeType == null || !TYPES_BY_ACTIVITY.get(activity).contains(programmeType)) {
            return programmeType + " is not a valid type for " + activity.toLowerCase().replace('_', ' ') + " training";
        }
        if (deliveryMode == null || !DELIVERY_MODES.contains(deliveryMode)) {
            return "Delivery mode must be one of " + String.join(", ", new java.util.TreeSet<>(DELIVERY_MODES));
        }
        if (ACCREDITED.equals(activity) && qualificationId == null) {
            return "An accredited programme must be linked to the qualification it is delivered under";
        }
        return null;
    }

    /** Why a qualification version cannot be made current, or null. */
    public static String qualificationActivationProblem(String sourceNote, String registrationStatus,
                                                        LocalDate registrationEnd, LocalDate today) {
        if (sourceNote == null || sourceNote.trim().length() < 5) {
            return "Say where this information came from, for example the register entry you checked";
        }
        if (registrationStatus == null || !REGISTRATION_STATUSES.contains(registrationStatus)) {
            return "Registration status is not recognised";
        }
        if (("REGISTERED".equals(registrationStatus) || "RE_REGISTERED".equals(registrationStatus))
                && registrationEnd != null && today != null && registrationEnd.isBefore(today)) {
            return "The registration end date has passed. Record the status as lapsed, or enter the new end date";
        }
        return null;
    }

    /**
     * Why a programme version cannot be approved, or null. It needs modules,
     * an accredited programme must be pinned to a published (not draft)
     * version of its own qualification, and a second person approves, or the
     * same person says why not.
     */
    public static String versionApprovalProblem(String activity, int moduleCount, UUID programmeQualificationId,
                                                UUID pinnedQualificationId, String pinnedQualificationVersionStatus,
                                                UUID actor, UUID creator, String overrideReason) {
        if (moduleCount < 1) {
            return "Add at least one module before approving";
        }
        if (ACCREDITED.equals(activity)) {
            if (pinnedQualificationVersionStatus == null) {
                return "Link this version to the qualification version it is delivered under";
            }
            if (!programmeQualificationId.equals(pinnedQualificationId)) {
                return "The linked qualification version belongs to a different qualification";
            }
            if ("DRAFT".equals(pinnedQualificationVersionStatus)) {
                return "The linked qualification version is still a draft";
            }
        }
        if (actor != null && actor.equals(creator) && (overrideReason == null || overrideReason.trim().length() < 10)) {
            return "A second person should approve a programme version. If no one else can, say why in at least 10 characters";
        }
        return null;
    }

    /** A soft note, never a refusal: electives and rules of combination make a plain sum unreliable. */
    public static String creditsNote(Integer versionCredits, int requiredModuleCredits, int allModuleCredits) {
        if (versionCredits == null) {
            return null;
        }
        if (requiredModuleCredits > versionCredits) {
            return "Required modules add up to " + requiredModuleCredits + " credits, more than the programme's " + versionCredits;
        }
        if (allModuleCredits < versionCredits) {
            return "All modules add up to " + allModuleCredits + " credits, fewer than the programme's " + versionCredits;
        }
        return null;
    }
}
