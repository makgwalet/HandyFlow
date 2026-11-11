package za.co.handyflow.platform.warehousing.application.internal;

import java.util.List;
import java.util.Locale;

/** What kinds of document can be attached to an inbound receipt, and the checks that go with them. */
public final class WhseEvidenceRules {

    private WhseEvidenceRules() {}

    /** Delivery note, photos of damage, packing list, advance shipping notice, other. */
    public static final List<String> INBOUND_TYPES = List.of("DELIVERY_NOTE", "DAMAGE_PHOTO", "PACKING_LIST", "ASN_DOCUMENT", "OTHER");

    /** The type in its stored form (upper case), or an error naming the choices. */
    public static String requireInboundType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!INBOUND_TYPES.contains(t)) {
            throw new IllegalArgumentException("Choose what the document is: " + String.join(", ", INBOUND_TYPES) + ".");
        }
        return t;
    }

    /** True when an upload is a type of file that can be opened as a document or photo. Names with a path are refused as well. */
    public static boolean acceptableFile(String fileName, String contentType) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return false;
        String c = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return c.startsWith("image/") || c.equals("application/pdf") || c.startsWith("text/") || c.equals("application/octet-stream")
                || c.contains("officedocument") || c.equals("application/msword") || c.equals("application/vnd.ms-excel");
    }
}
