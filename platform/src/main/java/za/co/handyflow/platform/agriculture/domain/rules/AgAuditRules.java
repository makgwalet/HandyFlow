package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Small pure rules for the "added by / changed by" stamp. */
public final class AgAuditRules {

    private AgAuditRules() {}

    public static final int MAX_NAME = 255;

    /** The record kinds that keep a stamp, as they appear in the address (/audit/{type}/{id}). */
    public static final List<String> TYPES = List.of("animals", "groups", "health-events", "breeding-records", "inventory-items", "crop-cycles",
            "weights", "mortality", "movements", "feed-records", "scouting", "harvests", "input-applications");

    public static final int MAX_BATCH = 200;

    public static String requireType(String type) {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(t)) throw new IllegalArgumentException("Unknown record type '" + type + "'. Use one of: " + String.join(", ", TYPES));
        return t;
    }

    /**
     * The name to keep with a stamp. TenantContext answers "Unknown" (or the user id) when it has no name; with no user id that
     * means nobody is signed in, so no name is kept. Names are cut to the column width.
     */
    public static String cleanName(UUID userId, String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (userId == null && "Unknown".equals(n)) return null;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    /** The ids of a batch request: at least one, at most MAX_BATCH, repeats ignored. */
    public static List<UUID> requireIds(List<UUID> ids) {
        List<UUID> distinct = ids == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(new LinkedHashSet<>(ids));
        distinct.removeIf(java.util.Objects::isNull);
        if (distinct.isEmpty()) throw new IllegalArgumentException("Give at least one id");
        if (distinct.size() > MAX_BATCH) throw new IllegalArgumentException("Ask for at most " + MAX_BATCH + " records at a time");
        return distinct;
    }
}
