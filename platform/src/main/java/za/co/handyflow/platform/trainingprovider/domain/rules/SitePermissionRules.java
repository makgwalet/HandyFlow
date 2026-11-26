package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The provider's own record of who may deliver, assess or moderate at which site. It sits beside the appointment checks and does not replace
 * them: an appointment says the person's credential for a programme was checked, a site permission says the provider has placed them at a site.
 * Neither is described as a regulatory approval.
 */
public final class SitePermissionRules {

    public static final int MIN_NOTE = 10;
    public static final String COVERED = "COVERED";
    public static final String NOT_RECORDED = "NOT_RECORDED";
    public static final String NOT_IN_FORCE = "NOT_IN_FORCE";

    private SitePermissionRules() {
    }

    /** One recorded permission. A null programme means any programme the person is appointed for. */
    public record Permission(UUID programmeId, LocalDate validFrom, LocalDate validTo, boolean active) {
    }

    public static String problem(String role, boolean practitionerActive, boolean siteActive, LocalDate from, LocalDate to, String basis) {
        if (role == null || !PractitionerRules.ROLES.contains(role)) {
            return "Choose a role";
        }
        if (!practitionerActive) {
            return "This person is inactive";
        }
        if (!siteActive) {
            return "That site is not active";
        }
        if (from == null) {
            return "Give the day the permission starts";
        }
        if (to != null && to.isBefore(from)) {
            return "The end date cannot be before the start date";
        }
        if (basis == null || basis.trim().length() < MIN_NOTE) {
            return "Say why the person is placed at this site (at least " + MIN_NOTE + " characters)";
        }
        return null;
    }

    public static String withdrawProblem(String status, String reason) {
        if ("WITHDRAWN".equals(status)) {
            return "This permission is already withdrawn";
        }
        return reason == null || reason.trim().length() < MIN_NOTE ? "Give a reason of at least " + MIN_NOTE + " characters" : null;
    }

    /** Whether the recorded permissions for a person, site and role cover a programme on a day. */
    public static String coverOn(List<Permission> recorded, UUID programmeId, LocalDate day) {
        boolean any = false;
        for (Permission p : recorded) {
            if (!p.active()) {
                continue;
            }
            if (p.programmeId() != null && !p.programmeId().equals(programmeId)) {
                continue;
            }
            any = true;
            if (!day.isBefore(p.validFrom()) && (p.validTo() == null || !day.isAfter(p.validTo()))) {
                return COVERED;
            }
        }
        return any ? NOT_IN_FORCE : NOT_RECORDED;
    }

    /** Plain wording. A missing record is a prompt to look, never a finding. */
    public static String note(String cover) {
        return switch (cover) {
            case COVERED -> "Placed at this site";
            case NOT_IN_FORCE -> "A site permission is recorded but not in force on the day";
            default -> "No site permission recorded for this person at this site";
        };
    }
}
