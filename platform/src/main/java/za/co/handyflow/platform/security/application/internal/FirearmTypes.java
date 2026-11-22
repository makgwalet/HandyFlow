package za.co.handyflow.platform.security.application.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The firearm types the register accepts. Mirrors the three categories a firearm competency is issued in
 * (Handgun, Shotgun, Rifle). Free text meant "Handgun", "handgun", "9mm pistol" and "Glock" all counted as different
 * types, so reports and competency matching could not rely on the field.
 */
public final class FirearmTypes {

    public static final List<String> ALLOWED = List.of("Handgun", "Shotgun", "Rifle");

    private static final Map<String, String> ALIASES = Map.of(
            "handgun", "Handgun", "pistol", "Handgun", "revolver", "Handgun",
            "shotgun", "Shotgun",
            "rifle", "Rifle");

    private FirearmTypes() {}

    /** The canonical type for a value as typed (case, spacing and the common synonyms forgiven), or null if unknown. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String k = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return ALIASES.get(k);
    }
}
