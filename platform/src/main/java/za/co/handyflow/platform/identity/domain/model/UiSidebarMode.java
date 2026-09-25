package za.co.handyflow.platform.identity.domain.model;

/** Sidebar width. MINI shows icons only. Values mirror the CHECK constraints in V302__ui_preferences.sql. */
public enum UiSidebarMode {
    FULL, MINI;

    /** Case-insensitive parse; null or blank returns null (meaning "not set"). */
    public static UiSidebarMode parseNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for UiSidebarMode; allowed: "
                    + java.util.Arrays.toString(values()));
        }
    }
}
