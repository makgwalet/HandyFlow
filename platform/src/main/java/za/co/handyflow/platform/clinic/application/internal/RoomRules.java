package za.co.handyflow.platform.clinic.application.internal;

/** Pure rules for consulting room names. */
final class RoomRules {

    static final int MAX_NAME = 60;

    private RoomRules() {}

    /** Trimmed, inner runs of spaces collapsed. Throws IllegalArgumentException for an empty or over-long name. */
    static String cleanName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw new IllegalArgumentException("A room needs a name");
        if (n.length() > MAX_NAME) throw new IllegalArgumentException("The room name is too long (at most " + MAX_NAME + " characters)");
        return n;
    }
}
