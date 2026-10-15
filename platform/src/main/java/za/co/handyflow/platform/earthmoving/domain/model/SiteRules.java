package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;

/** Rules for sites, and for matching the free text typed on older deployments to site and customer records. */
public final class SiteRules {
    private SiteRules() {}

    /** Trim, collapse runs of spaces, lower-case: the same name typed slightly differently compares equal. Never used to guess between different names. */
    public static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    public enum MatchState { NONE, MATCHED, AMBIGUOUS, UNMATCHED }

    /** NONE: nothing to match. MATCHED: exactly one record. AMBIGUOUS: several, so a person must choose. UNMATCHED: no record. */
    public static MatchState classify(String legacyText, int candidates) {
        if (legacyText == null || legacyText.isBlank()) return MatchState.NONE;
        return candidates == 1 ? MatchState.MATCHED : candidates > 1 ? MatchState.AMBIGUOUS : MatchState.UNMATCHED;
    }

    public static void checkSite(String name, BigDecimal latitude, BigDecimal longitude) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A site needs a name.");
        if (name.trim().length() > 255) throw new IllegalArgumentException("The site name is too long.");
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("Give both latitude and longitude, or neither.");
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0))
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0))
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
    }
}
