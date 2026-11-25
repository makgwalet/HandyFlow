package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Set;

/**
 * Data-quality checks on a learner record. These check the shape of what was typed (an ID number's check
 * digit, a date of birth that agrees with it, a consent that has a date). They never decide eligibility,
 * funding or anything a regulator defines.
 */
public final class LearnerRules {

    public static final Set<String> ID_TYPES = Set.of("SA_ID", "PASSPORT", "OTHER", "NONE");
    public static final int MIN_REASON = 10;

    private LearnerRules() {
    }

    /** Luhn check over all 13 digits, as used for South African ID numbers. */
    public static boolean luhnValid(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** Why the identity details are refused, or null. */
    public static String idProblem(String idType, String idNumber, LocalDate dob, LocalDate today) {
        if (idType == null || !ID_TYPES.contains(idType)) {
            return "Choose how the learner is identified";
        }
        String n = idNumber == null ? "" : idNumber.trim();
        if ("NONE".equals(idType)) {
            if (!n.isEmpty()) {
                return "Leave the number blank when the learner has no identity document";
            }
        } else if (n.length() < 3) {
            return "Enter the identity number";
        }
        if (dob != null && today != null && dob.isAfter(today)) {
            return "The date of birth cannot be in the future";
        }
        if ("SA_ID".equals(idType)) {
            if (!n.matches("\\d{13}")) {
                return "A South African ID number has 13 digits";
            }
            if (!luhnValid(n)) {
                return "That ID number does not pass its check digit. Please re-check it";
            }
            int yy = Integer.parseInt(n.substring(0, 2));
            int mm = Integer.parseInt(n.substring(2, 4));
            int dd = Integer.parseInt(n.substring(4, 6));
            boolean dateOk = false;
            for (int century : new int[]{1900, 2000}) {
                try {
                    LocalDate d = LocalDate.of(century + yy, mm, dd);
                    if (today == null || !d.isAfter(today)) {
                        dateOk = true;
                        if (dob != null && dob.equals(d)) {
                            return null;
                        }
                    }
                } catch (DateTimeException ignored) {
                    // not a real date in this century
                }
            }
            if (!dateOk) {
                return "The date inside that ID number is not a real date";
            }
            if (dob != null) {
                return "The date of birth does not match the date inside the ID number";
            }
        }
        return null;
    }

    public static String contactProblem(String email) {
        if (email != null && !email.isBlank() && !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "That email address does not look right";
        }
        return null;
    }

    /** Consent to hold the learner's details must carry the date it was given. */
    public static String consentProblem(boolean given, LocalDate on, LocalDate today) {
        if (given && on == null) {
            return "Enter the date the learner gave consent";
        }
        if (!given && on != null) {
            return "A consent date was entered without consent being ticked";
        }
        if (on != null && today != null && on.isAfter(today)) {
            return "The consent date cannot be in the future";
        }
        return null;
    }

    public static String namesProblem(String first, String surname) {
        if (first == null || first.trim().length() < 2 || surname == null || surname.trim().length() < 2) {
            return "Enter the learner's first names and surname";
        }
        return null;
    }

    public static boolean longEnough(String s, int n) {
        return s != null && s.trim().length() >= n;
    }

    /** Shows only the last four characters, for lists. */
    public static String mask(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            return null;
        }
        String n = idNumber.trim();
        return n.length() <= 4 ? "****" : "*".repeat(n.length() - 4) + n.substring(n.length() - 4);
    }
}
