package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where received stock should go. Pure, no I/O.
 *
 * <p>A destination must be an active storage location (not a dock or staging area), must not hold another client's stock (a 3PL does not mix
 * clients in one location) and, where a capacity is set, must have room for the quantity. Among the eligible ones: a location that already
 * holds the item comes first (keeps the item together), then an empty one, then one holding other items of the same client. Ties go to the
 * lowest picking sequence (closest to the start of the walk), then code.
 */
public final class WhsePutawayRules {

    private WhsePutawayRules() {}

    public static final Set<String> STORAGE_TYPES = Set.of("BIN", "SHELF", "PALLET", "FLOOR");

    public record Candidate(UUID locationId, String code, String type, boolean active, BigDecimal capacity, BigDecimal onHand,
                            Integer pickSequence, boolean holdsSameItem, boolean holdsOtherClient) {}

    public record Suggestion(UUID locationId, String code, String reason, BigDecimal roomLeft) {}

    /** Null when the candidate can take the quantity, otherwise what is wrong with it. */
    public static String problem(Candidate c, BigDecimal qty) {
        if (!c.active()) return c.code() + " is not active.";
        if (!STORAGE_TYPES.contains(c.type())) return c.code() + " is a " + c.type().toLowerCase() + " location, not storage.";
        if (c.holdsOtherClient()) return c.code() + " holds another client's stock.";
        if (c.capacity() != null) {
            BigDecimal room = c.capacity().subtract(c.onHand());
            if (room.compareTo(qty) < 0) return c.code() + " has room for " + plain(room.max(BigDecimal.ZERO)) + ", not " + plain(qty) + ".";
        }
        return null;
    }

    public static List<Suggestion> rank(List<Candidate> candidates, BigDecimal qty, int limit) {
        List<Candidate> ok = new ArrayList<>();
        for (Candidate c : candidates) if (problem(c, qty) == null) ok.add(c);
        ok.sort(Comparator.comparingInt(WhsePutawayRules::group)
                .thenComparing(Candidate::pickSequence, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Candidate::code));
        List<Suggestion> out = new ArrayList<>();
        for (Candidate c : ok) {
            if (out.size() >= limit) break;
            BigDecimal room = c.capacity() == null ? null : c.capacity().subtract(c.onHand());
            out.add(new Suggestion(c.locationId(), c.code(), reason(c), room));
        }
        return out;
    }

    private static int group(Candidate c) {
        if (c.holdsSameItem()) return 0;
        if (c.onHand().signum() == 0) return 1;
        return 2;
    }

    private static String reason(Candidate c) {
        if (c.holdsSameItem()) return "Already holds this item";
        if (c.onHand().signum() == 0) return "Empty";
        return "Holds other items of this client";
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
