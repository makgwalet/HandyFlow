package za.co.handyflow.platform.identity.domain.model;

/** Colour scheme. SYSTEM follows the operating system setting. Values mirror the CHECK constraints in V302__ui_preferences.sql. */
public enum UiThemeMode {
    LIGHT, DARK, SYSTEM;

    /** Case-insensitive parse; null or blank returns null (meaning "not set"). */
    public static UiThemeMode parseNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for UiThemeMode; allowed: "
                    + java.util.Arrays.toString(values()));
        }
    }
}
