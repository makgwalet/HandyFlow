package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Slotting: put the items that are picked most in the locations that are nearest the front of the pick walk. Pure, no I/O.
 * <p>
 * Items are classed by pick volume over a period (ABC): A are the items that together make up the first 80% of units picked, B the next 15%,
 * C the rest, and items not picked at all are C. Only A and B items are offered a better spot, and only an empty one, so a suggestion is a
 * plain move and never a swap. Location quality is its pick sequence (walking order, lower is nearer); a location off the walk (no sequence) is last.
 */
public final class WhseSlottingRules {

    private WhseSlottingRules() {}

    public static final BigDecimal A_SHARE = new BigDecimal("0.80");
    public static final BigDecimal B_SHARE = new BigDecimal("0.95");

    /** Class of each item by pick volume. Items with no picks are C. */
    public static Map<UUID, String> classify(Map<UUID, BigDecimal> picked, Set<UUID> allItems) {
        Map<UUID, String> out = new LinkedHashMap<>();
        BigDecimal total = picked.values().stream().filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (UUID i : allItems) out.put(i, "C");
        if (total.signum() == 0) return out;
        List<Map.Entry<UUID, BigDecimal>> ranked = new ArrayList<>(picked.entrySet().stream().filter(e -> e.getValue().signum() > 0).toList());
        ranked.sort(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed().thenComparing(e -> e.getKey().toString()));
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> e : ranked) {
            // The item that crosses a threshold is in the class it starts in: the first item is always A.
            String cls = running.compareTo(total.multiply(A_SHARE)) < 0 ? "A" : running.compareTo(total.multiply(B_SHARE)) < 0 ? "B" : "C";
            out.put(e.getKey(), cls);
            running = running.add(e.getValue());
        }
        return out;
    }

    /** An item's stock sitting in one storage location, as a single position (one lot, one status). */
    public record Placed(UUID clientId, UUID itemId, UUID lotId, UUID locationId, String locationCode, Integer pickSequence, BigDecimal available, BigDecimal picked, String abc) {}
    /** A storage location with nothing in it and nothing on its way. */
    public record Empty(UUID locationId, String code, Integer pickSequence, BigDecimal capacity) {}
    public record Move(Placed from, Empty to, int improvement) {}

    private static int rank(Integer seq) { return seq == null ? Integer.MAX_VALUE : seq; }

    /**
     * For the busiest items first, the nearest empty location that is nearer than where the item is now by at least {@code minImprovement} places
     * in the walk and big enough for what would move. Each empty location is offered once.
     */
    public static List<Move> plan(List<Placed> placed, List<Empty> empties, int minImprovement) {
        List<Placed> candidates = new ArrayList<>(placed.stream()
                .filter(p -> !"C".equals(p.abc()) && p.available().signum() > 0 && p.picked().signum() > 0).toList());
        candidates.sort(Comparator.comparing(Placed::picked).reversed().thenComparing(p -> p.itemId().toString()));
        List<Empty> free = new ArrayList<>(empties.stream().filter(e -> e.pickSequence() != null).toList());
        free.sort(Comparator.comparing(Empty::pickSequence).thenComparing(Empty::code));
        Set<UUID> used = new HashSet<>();
        Set<UUID> movedItems = new HashSet<>();
        List<Move> moves = new ArrayList<>();
        for (Placed p : candidates) {
            if (movedItems.contains(p.itemId())) continue;
            for (Empty e : free) {
                if (used.contains(e.locationId())) continue;
                if (e.locationId().equals(p.locationId())) continue;
                long gain = (long) rank(p.pickSequence()) - e.pickSequence();
                if (gain < minImprovement) break; // free is nearest-first, so nothing later is better
                if (e.capacity() != null && e.capacity().compareTo(p.available()) < 0) continue;
                used.add(e.locationId());
                movedItems.add(p.itemId());
                moves.add(new Move(p, e, (int) Math.min(gain, Integer.MAX_VALUE)));
                break;
            }
        }
        return moves;
    }
}
