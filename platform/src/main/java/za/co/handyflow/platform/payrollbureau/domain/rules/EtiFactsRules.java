package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.time.DateTimeException;
import java.time.LocalDate;

/** Works out the ETI facts that can be read from an ID number. */
public final class EtiFactsRules {

    private EtiFactsRules() {}

    /** A 13-digit South African ID number with a valid date of birth and check digit. */
    public static boolean isValidSaId(String id) {
        if (id == null) return false;
        String s = id.trim();
        if (!s.matches("\\d{13}")) return false;
        if (dateOfBirth(s, LocalDate.of(2100, 1, 1)) == null) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = s.charAt(12 - i) - '0';
            if (i % 2 == 1) { digit *= 2; if (digit > 9) digit -= 9; }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    /** The date of birth in the first six digits; the century is the latest that is not after the reference date. Null if not a date. */
    public static LocalDate dateOfBirth(String id, LocalDate reference) {
        if (id == null || id.trim().length() < 6 || !id.trim().substring(0, 6).matches("\\d{6}")) return null;
        String s = id.trim();
        int yy = Integer.parseInt(s.substring(0, 2));
        int mm = Integer.parseInt(s.substring(2, 4));
        int dd = Integer.parseInt(s.substring(4, 6));
        int century = (reference.getYear() / 100) * 100;
        try {
            LocalDate d = LocalDate.of(century + yy, mm, dd);
            if (d.isAfter(reference)) d = LocalDate.of(century - 100 + yy, mm, dd);
            return d;
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** The manual choice if there is one, else SA_ID for a valid SA ID number, else OTHER. */
    public static String idKind(String idNumber, String manualChoice) {
        if (manualChoice != null && !manualChoice.isBlank()) return manualChoice;
        return isValidSaId(idNumber) ? EtiRules.SA_ID : EtiRules.OTHER;
    }
}
