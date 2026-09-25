package za.co.handyflow.platform.identity.domain.model;

/** Curated brand colours. Each maps to a contrast-checked palette in the frontend (src/styles/brand.ts); arbitrary colours are deliberately not supported so tenants cannot choose unreadable combinations. NAVY is the existing HandyFlow colour. Values mirror the CHECK constraints in V302__ui_preferences.sql. */
public enum UiBrandColor {
    NAVY, OCEAN, TEAL, VIOLET, EMERALD, CHARCOAL;

    /** Case-insensitive parse; null or blank returns null (meaning "not set"). */
    public static UiBrandColor parseNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for UiBrandColor; allowed: "
                    + java.util.Arrays.toString(values()));
        }
    }
}
