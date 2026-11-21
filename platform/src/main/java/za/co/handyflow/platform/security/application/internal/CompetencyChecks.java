package za.co.handyflow.platform.security.application.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Is a guard competent to carry this firearm? Pure, so it is testable without Spring.
 * A competency certificate is issued per firearm category (handgun, shotgun, rifle).
 *
 * Legacy mode (strict = false), so existing data keeps working:
 *   - a certificate with no category counts for every category;
 *   - the old guard-level competency date counts for every category;
 *   - verification is not required.
 * Strict mode: only a verified certificate that names a category counts, and only for that category.
 */
final class CompetencyChecks {

    static final Set<String> CATEGORIES = Set.of("HANDGUN", "SHOTGUN", "RIFLE");

    /** One in-force FIREARM_COMPETENCY row: category may be null on older rows. */
    record Cert(String category, LocalDate expiry, boolean verified) {}

    private CompetencyChecks() {}

    /** HANDGUN, SHOTGUN or RIFLE from the register's free-text firearm type; null when it cannot be told. */
    static String categoryOf(String firearmType) {
        if (firearmType == null) return null;
        String t = firearmType.toLowerCase(Locale.ROOT);
        if (t.contains("shotgun") || t.contains("shot gun")) return "SHOTGUN";
        if (t.contains("rifle") || t.contains("carbine") || t.contains("semi-auto") || t.contains("semi auto")) return "RIFLE";
        if (t.contains("handgun") || t.contains("hand gun") || t.contains("pistol") || t.contains("revolver")) return "HANDGUN";
        return null;
    }

    /** Upper-cased valid category, null when blank. Throws on anything else. */
    static String parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(c)) throw new IllegalArgumentException("Firearm category must be HANDGUN, SHOTGUN or RIFLE");
        return c;
    }

    /**
     * @return null when competent, else NO_FIREARM_COMPETENCY (nothing valid on file)
     *         or NO_COMPETENCY_FOR_CATEGORY (valid for other categories, not this one)
     */
    static String check(String category, List<Cert> certs, boolean legacyGuardLevelValid, boolean strict, LocalDate today) {
        List<Cert> valid = certs.stream()
                .filter(c -> c.expiry() != null && !c.expiry().isBefore(today))
                .filter(c -> !strict || (c.verified() && c.category() != null))
                .toList();

        if (!strict && legacyGuardLevelValid) return null;
        if (category == null) return valid.isEmpty() ? "NO_FIREARM_COMPETENCY" : null;

        boolean covered = valid.stream().anyMatch(c ->
                category.equals(c.category()) || (c.category() == null && !strict));
        if (covered) return null;
        return valid.isEmpty() ? "NO_FIREARM_COMPETENCY" : "NO_COMPETENCY_FOR_CATEGORY";
    }
}
