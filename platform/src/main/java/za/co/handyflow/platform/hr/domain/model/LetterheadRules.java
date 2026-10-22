package za.co.handyflow.platform.hr.domain.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * What goes at the top of a letter: the company's name, address and contact lines, and its logo when it has a usable one.
 * Pure, so the wording and the checks on the logo run without a database or a PDF library.
 */
public final class LetterheadRules {

    public static final int MAX_LOGO_BYTES = 512 * 1024;

    private LetterheadRules() {}

    /** The name, the lines beneath it (address, contact, VAT) and the logo image bytes, or null for no logo. */
    public record Letterhead(String name, List<String> lines, byte[] logo) {
        public static Letterhead plain(String name) { return new Letterhead(name, List.of(), null); }
        public boolean hasLogo() { return logo != null; }
    }

    public static Letterhead of(String name, Map<String, String> address, String phone, String email, String vatNumber, String logoDataUri) {
        List<String> lines = new ArrayList<>();
        String addr = addressLine(address);
        if (addr != null) lines.add(addr);
        String contact = contactLine(phone, email);
        if (contact != null) lines.add(contact);
        if (vatNumber != null && !vatNumber.isBlank()) lines.add("VAT no. " + vatNumber.trim());
        return new Letterhead(name, List.copyOf(lines), decodeLogo(logoDataUri));
    }

    /** "12 Mining Road, Germiston, Johannesburg, Gauteng, 1401", or null when there is nothing to show. */
    public static String addressLine(Map<String, String> address) {
        if (address == null) return null;
        List<String> parts = new ArrayList<>();
        for (String k : List.of("street", "suburb", "city", "province", "postalCode")) {
            String v = address.get(k);
            if (v != null && !v.isBlank()) parts.add(v.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    public static String contactLine(String phone, String email) {
        List<String> parts = new ArrayList<>();
        if (phone != null && !phone.isBlank()) parts.add("Tel " + phone.trim());
        if (email != null && !email.isBlank()) parts.add(email.trim());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * The picture bytes of a PNG or JPEG sent as a data address ("data:image/png;base64,..."), or null. Anything else, anything
     * larger than {@link #MAX_LOGO_BYTES}, and anything whose first bytes are not those of the type it claims is left out, so a bad
     * logo can never stop a letter from being issued.
     */
    public static byte[] decodeLogo(String dataUri) {
        if (dataUri == null || !dataUri.startsWith("data:image/")) return null;
        int comma = dataUri.indexOf(',');
        if (comma < 0) return null;
        String header = dataUri.substring(0, comma).toLowerCase();
        if (!header.endsWith(";base64")) return null;
        boolean png = header.startsWith("data:image/png");
        boolean jpeg = header.startsWith("data:image/jpeg") || header.startsWith("data:image/jpg");
        if (!png && !jpeg) return null;
        String payload = dataUri.substring(comma + 1).strip();
        if (payload.length() > (long) MAX_LOGO_BYTES * 4 / 3 + 8) return null;
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) return null;
        if (png && !startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) return null;
        if (jpeg && !startsWith(bytes, 0xFF, 0xD8, 0xFF)) return null;
        return bytes;
    }

    private static boolean startsWith(byte[] b, int... magic) {
        if (b.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) if ((b[i] & 0xFF) != magic[i]) return false;
        return true;
    }
}
