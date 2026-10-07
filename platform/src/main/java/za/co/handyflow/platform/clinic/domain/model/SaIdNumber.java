package za.co.handyflow.platform.clinic.domain.model;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;

/**
 * South African 13-digit ID number: YYMMDD SSSS C A Z.
 * Checks the structure (digits, real date, citizenship digit, Luhn checksum). It does not
 * prove the number was issued to anyone. Passports and other documents are not 13 digits
 * and are not validated here.
 */
public record SaIdNumber(String value, LocalDate dateOfBirth, boolean male, boolean citizen) {

    /** A value is treated as an SA ID when it is exactly 13 digits; anything else is another document. */
    public static boolean looksLikeSaId(String s) {
        return s != null && s.trim().matches("\\d{13}");
    }

    /** Parsed ID, or null when the value is not a valid SA ID (see {@link #problem}). */
    public static SaIdNumber parseOrNull(String s) {
        return problem(s) == null ? parse(s.trim()) : null;
    }

    /** Human-readable reason the value is not a valid SA ID, or null when it is valid. */
    public static String problem(String s) {
        if (!looksLikeSaId(s)) return "An SA ID number is exactly 13 digits.";
        String v = s.trim();
        if (candidateDob(v) == null) return "The first six digits are not a real date of birth (YYMMDD).";
        char c = v.charAt(10);
        if (c != '0' && c != '1') return "The citizenship digit (11th) must be 0 or 1.";
        if (!luhn(v)) return "The check digit is wrong; please re-check the number.";
        return null;
    }

    private static SaIdNumber parse(String v) {
        int seq = Integer.parseInt(v.substring(6, 10));
        return new SaIdNumber(v, candidateDob(v), seq >= 5000, v.charAt(10) == '0');
    }

    /** Date of birth from the number; two-digit years in the future are read as 19xx, otherwise 20xx. */
    static LocalDate candidateDob(String v) {
        int yy = Integer.parseInt(v.substring(0, 2));
        int mm = Integer.parseInt(v.substring(2, 4));
        int dd = Integer.parseInt(v.substring(4, 6));
        int year = 2000 + yy;
        if (year > Year.now().getValue()) year -= 100;
        try {
            LocalDate d = LocalDate.of(year, mm, dd);
            if (d.isAfter(LocalDate.now())) d = d.minusYears(100);
            return d;
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** True when the given date matches the number's YYMMDD in either century. */
    public boolean matchesDateOfBirth(LocalDate other) {
        return other != null
                && other.getMonthValue() == dateOfBirth.getMonthValue()
                && other.getDayOfMonth() == dateOfBirth.getDayOfMonth()
                && other.getYear() % 100 == dateOfBirth.getYear() % 100;
    }

    private static boolean luhn(String v) {
        int sum = 0;
        for (int i = 0; i < v.length(); i++) {
            int d = v.charAt(v.length() - 1 - i) - '0';
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        return sum % 10 == 0;
    }
}
