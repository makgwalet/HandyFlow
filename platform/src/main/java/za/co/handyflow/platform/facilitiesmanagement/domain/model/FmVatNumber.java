package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import java.util.regex.Pattern;

/**
 * South African VAT registration numbers are ten digits and start with 4. Normalising here means a number
 * typed as "4123 456 789" is stored and printed the same way everywhere.
 */
public final class FmVatNumber {

    private static final Pattern FORMAT = Pattern.compile("^4\\d{9}$");

    private FmVatNumber() {}

    /** Returns the normalised number, or null for a blank value (meaning "not VAT registered / not supplied"). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[\\s-]", "");
        if (!FORMAT.matcher(digits).matches()) {
            throw new IllegalArgumentException("A South African VAT number is 10 digits starting with 4 — got '" + raw.trim() + "'");
        }
        return digits;
    }
}
