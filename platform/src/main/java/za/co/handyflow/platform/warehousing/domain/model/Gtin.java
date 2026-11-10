package za.co.handyflow.platform.warehousing.domain.model;

/** GTIN-8/12/13/14 (EAN-8, UPC-A, EAN-13, ITF-14) validation using the standard mod-10 check digit. */
public final class Gtin {

    private Gtin() {}

    /** @return the trimmed barcode, or null when blank. Throws if it is not a valid GTIN. */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (!isValid(s)) {
            throw new IllegalArgumentException("'" + s + "' is not a valid barcode. Use an 8, 12, 13 or 14 digit GTIN with a correct check digit.");
        }
        return s;
    }

    public static boolean isValid(String s) {
        if (s == null) return false;
        int n = s.length();
        if (n != 8 && n != 12 && n != 13 && n != 14) return false;
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        int sum = 0;
        // From the digit left of the check digit, weights alternate 3, 1, 3, ...
        for (int i = n - 2, w = 3; i >= 0; i--, w = (w == 3 ? 1 : 3)) {
            sum += (s.charAt(i) - '0') * w;
        }
        return (10 - sum % 10) % 10 == s.charAt(n - 1) - '0';
    }
}
