package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for practitioner appointments. A credential reference is recorded as typed. An appointment counts as
 * in force only when someone has recorded where it was checked and its dates cover the day. Typing a number
 * never makes anyone registered or competent to assess.
 */
public final class PractitionerRules {

    public static final String FACILITATOR = "FACILITATOR";
    public static final String ASSESSOR = "ASSESSOR";
    public static final String MODERATOR = "MODERATOR";
    public static final Set<String> ROLES = Set.of(FACILITATOR, ASSESSOR, MODERATOR);
    public static final Set<String> EMPLOYMENT = Set.of("EMPLOYEE", "CONTRACTOR", "SECONDED");
    public static final int MIN_REASON = 10;
    public static final int MIN_SOURCE = 5;

    public enum Standing {
        VERIFIED_IN_FORCE("Credential checked and in force"),
        NOT_VERIFIED("Not checked: no record of where the credential was verified"),
        NOT_YET_VALID("Not yet in force"),
        EXPIRED("Expired"),
        WITHDRAWN("Withdrawn");

        private final String note;

        Standing(String note) {
            this.note = note;
        }

        public String note() {
            return note;
        }
    }

    private PractitionerRules() {
    }

    public static boolean longEnough(String s, int n) {
        return s != null && s.trim().length() >= n;
    }

    public static boolean needsCredential(String role) {
        return ASSESSOR.equals(role) || MODERATOR.equals(role);
    }

    /** What an appointment is worth on a given day. Withdrawal and dates are checked before verification. */
    public static Standing standing(String status, LocalDate verifiedOn, LocalDate validFrom, LocalDate validTo, LocalDate on) {
        if ("WITHDRAWN".equals(status)) {
            return Standing.WITHDRAWN;
        }
        if (on != null && validTo != null && validTo.isBefore(on)) {
            return Standing.EXPIRED;
        }
        if (on != null && validFrom != null && validFrom.isAfter(on)) {
            return Standing.NOT_YET_VALID;
        }
        return verifiedOn == null ? Standing.NOT_VERIFIED : Standing.VERIFIED_IN_FORCE;
    }

    public static String namesProblem(String first, String surname) {
        if (first == null || first.trim().length() < 2 || surname == null || surname.trim().length() < 2) {
            return "Enter the person's first names and surname";
        }
        return null;
    }

    public static String contactProblem(String employmentType, String email) {
        if (employmentType == null || !EMPLOYMENT.contains(employmentType)) {
            return "Choose how the person is engaged";
        }
        if (email != null && !email.isBlank() && !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "That email address does not look right";
        }
        return null;
    }

    public static String appointmentProblem(String role, LocalDate validFrom, LocalDate validTo) {
        if (role == null || !ROLES.contains(role)) {
            return "Choose facilitator, assessor or moderator";
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            return "The appointment cannot end before it starts";
        }
        return null;
    }

    /** Why a verification record is refused, or null. */
    public static String verifyProblem(String role, String credentialRef, String source, LocalDate verifiedOn, LocalDate today) {
        if (!longEnough(source, MIN_SOURCE)) {
            return "Say where the credential was checked, for example the authority's register, in at least " + MIN_SOURCE + " characters";
        }
        if (verifiedOn == null) {
            return "Enter the date it was checked";
        }
        if (today != null && verifiedOn.isAfter(today)) {
            return "The date it was checked cannot be in the future";
        }
        if (needsCredential(role) && !longEnough(credentialRef, 3)) {
            return "Record the credential reference that was checked before verifying an assessor or moderator";
        }
        return null;
    }

    public static String reasonProblem(String reason) {
        return longEnough(reason, MIN_REASON) ? null : "Say why, in at least " + MIN_REASON + " characters";
    }
}
