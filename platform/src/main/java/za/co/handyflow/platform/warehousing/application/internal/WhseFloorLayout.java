package za.co.handyflow.platform.warehousing.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates a block of racking: a run of aisles, each with bays and levels, laid out on the floor grid with a walking (snake) order for
 * picking. Aisles are rows on the grid, bays run along a row, levels of one bay share a cell. Pure, no I/O.
 */
public final class WhseFloorLayout {

    private WhseFloorLayout() {}

    public static final int MAX_SLOTS = 2000;
    public static final Set<String> TYPES = Set.of("BIN", "SHELF", "PALLET", "FLOOR", "DOCK", "STAGING");
    private static final Pattern AISLE = Pattern.compile("[A-Za-z0-9]{1,6}");

    public record Spec(String zone, List<String> aisles, int bays, int levels, String type, int originX, int originY, int aisleSpacing) {}

    public record Slot(String code, String zone, String aisle, int bay, int level, String type, int x, int y, int pickSequence) {}

    public static String code(String aisle, int bay, int level) {
        return String.format("%s-%02d-%02d", aisle, bay, level);
    }

    /** Checks the spec and returns the trimmed, upper-cased aisle names. */
    public static List<String> validate(Spec s) {
        if (s.aisles() == null || s.aisles().isEmpty()) throw new IllegalArgumentException("Give at least one aisle.");
        if (s.bays() < 1 || s.bays() > 99) throw new IllegalArgumentException("Bays per aisle must be between 1 and 99.");
        if (s.levels() < 1 || s.levels() > 10) throw new IllegalArgumentException("Levels must be between 1 and 10.");
        if (s.type() == null || !TYPES.contains(s.type())) throw new IllegalArgumentException("Location type must be one of " + new java.util.TreeSet<>(TYPES) + ".");
        if (s.originX() < 0 || s.originY() < 0) throw new IllegalArgumentException("The map origin cannot be negative.");
        if (s.aisleSpacing() < 1 || s.aisleSpacing() > 10) throw new IllegalArgumentException("Aisle spacing must be between 1 and 10 rows.");
        List<String> names = new ArrayList<>();
        for (String a : s.aisles()) {
            String n = a == null ? "" : a.trim().toUpperCase();
            if (!AISLE.matcher(n).matches()) throw new IllegalArgumentException("Aisle names are 1 to 6 letters or digits: '" + a + "' is not valid.");
            if (names.contains(n)) throw new IllegalArgumentException("Aisle " + n + " is listed twice.");
            names.add(n);
        }
        if ((long) names.size() * s.bays() * s.levels() > MAX_SLOTS) throw new IllegalArgumentException("That is more than " + MAX_SLOTS + " locations in one go. Generate it in parts.");
        return names;
    }

    /**
     * Builds the slots. The walking order snakes: the first aisle is walked bay 1 to the end, the next from the end back to bay 1, and so on;
     * within a bay, the lowest level comes first. Numbering starts at {@code startSequence}.
     */
    public static List<Slot> generate(Spec s, int startSequence) {
        List<String> aisles = validate(s);
        List<Slot> out = new ArrayList<>();
        int seq = startSequence;
        String zone = s.zone() == null || s.zone().isBlank() ? null : s.zone().trim();
        for (int i = 0; i < aisles.size(); i++) {
            boolean forward = i % 2 == 0;
            for (int k = 0; k < s.bays(); k++) {
                int bay = forward ? k + 1 : s.bays() - k;
                for (int level = 1; level <= s.levels(); level++) {
                    out.add(new Slot(code(aisles.get(i), bay, level), zone, aisles.get(i), bay, level, s.type(),
                            s.originX() + bay - 1, s.originY() + i * s.aisleSpacing(), seq++));
                }
            }
        }
        return out;
    }
}
