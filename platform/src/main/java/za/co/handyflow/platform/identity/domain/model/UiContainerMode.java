package za.co.handyflow.platform.identity.domain.model;

/** Page width. BOXED caps content width; FULL uses the whole viewport. Values mirror the CHECK constraints in V302__ui_preferences.sql. */
public enum UiContainerMode {
    BOXED, FULL;

    /** Case-insensitive parse; null or blank returns null (meaning "not set"). */
    public static UiContainerMode parseNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for UiContainerMode; allowed: "
                    + java.util.Arrays.toString(values()));
        }
    }
}
