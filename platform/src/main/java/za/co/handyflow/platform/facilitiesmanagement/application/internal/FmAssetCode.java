package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import java.util.UUID;

/**
 * Reads whatever a scanner or a person typed for an asset: the QR payload {@code HF:FMASSET:<uuid>}, a bare asset
 * id, a link ending in the asset id, or the asset tag printed on the label.
 */
public final class FmAssetCode {

    public static final String PREFIX = "HF:FMASSET:";
    public static final int MAX_LENGTH = 200;

    private FmAssetCode() {}

    /** Exactly one of {@code id} and {@code tag} is set. */
    public record Parsed(UUID id, String tag) {}

    public static String payloadFor(UUID assetId) { return PREFIX + assetId; }

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Scan or enter an asset code");
        String code = raw.trim();
        if (code.length() > MAX_LENGTH) throw new IllegalArgumentException("That code is too long to be an asset code");
        if (code.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) code = code.substring(PREFIX.length()).trim();
        else if (code.contains("/")) {
            String last = code.substring(code.lastIndexOf('/') + 1).trim();
            if (!last.isEmpty()) code = last;
        }
        UUID id = tryUuid(code);
        if (id != null) return new Parsed(id, null);
        if (code.isEmpty()) throw new IllegalArgumentException("Scan or enter an asset code");
        return new Parsed(null, code);
    }

    private static UUID tryUuid(String s) {
        if (s.length() != 36) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
