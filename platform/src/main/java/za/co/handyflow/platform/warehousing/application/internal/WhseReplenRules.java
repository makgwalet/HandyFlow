package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Working out replenishment for one pick face. Pure, no I/O. */
public final class WhseReplenRules {

    private WhseReplenRules() {}

    /** Stock in a reserve location that could be moved to the face. {@code lotExpiry}-ordering keeps first-expiry-first-out for lot-tracked items. */
    public record Source(UUID locationId, UUID lotId, java.time.LocalDate lotExpiry, Integer pickSequence, BigDecimal available) {}
    public record Move(Source source, BigDecimal qty) {}
    public record Plan(List<Move> moves, BigDecimal uncovered) {
        public BigDecimal planned() { return moves.stream().map(Move::qty).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    public static void checkLevels(BigDecimal min, BigDecimal max) {
        if (min == null || min.signum() < 0) throw new IllegalArgumentException("The minimum must be zero or more.");
        if (max == null || max.signum() <= 0) throw new IllegalArgumentException("Enter the maximum.");
        if (max.compareTo(min) <= 0) throw new IllegalArgumentException("The maximum must be more than the minimum.");
        if (max.compareTo(new BigDecimal("999999999")) > 0 || min.scale() > 3 || max.scale() > 3) throw new IllegalArgumentException("Levels have at most 3 decimals and must be below a billion.");
    }

    /** True when the face needs filling: what is available there, plus what is already on its way, is below the minimum. */
    public static boolean needsReplenishment(BigDecimal faceAvailable, BigDecimal inbound, BigDecimal min) {
        return faceAvailable.add(inbound).compareTo(min) < 0;
    }

    /**
     * Fills the face to its maximum from the sources. Lots with the earliest expiry go first (lots without an expiry last), then the biggest
     * positions, so whole pallets move rather than scraps; ties follow the pick sequence (the furthest reserve first is not assumed).
     * {@code uncovered} is what the reserve could not supply.
     */
    public static Plan plan(BigDecimal faceAvailable, BigDecimal inbound, BigDecimal min, BigDecimal max, List<Source> sources) {
        if (!needsReplenishment(faceAvailable, inbound, min)) return new Plan(List.of(), BigDecimal.ZERO);
        BigDecimal need = max.subtract(faceAvailable).subtract(inbound);
        List<Source> usable = new ArrayList<>(sources.stream().filter(s -> s.available() != null && s.available().signum() > 0).toList());
        usable.sort(Comparator.comparing(Source::lotExpiry, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Source::available, Comparator.reverseOrder())
                .thenComparing(Source::pickSequence, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.locationId().toString()));
        List<Move> moves = new ArrayList<>();
        BigDecimal left = need;
        for (Source s : usable) {
            if (left.signum() <= 0) break;
            BigDecimal take = s.available().min(left);
            moves.add(new Move(s, take));
            left = left.subtract(take);
        }
        return new Plan(moves, left.max(BigDecimal.ZERO));
    }

    /** OK, BELOW_MIN (tasks needed or on the way) or NO_RESERVE (below the minimum and nothing to fill it from). */
    public static String status(BigDecimal faceAvailable, BigDecimal inbound, BigDecimal min, BigDecimal uncovered) {
        if (!needsReplenishment(faceAvailable, inbound, min)) return inbound.signum() > 0 ? "FILLING" : "OK";
        return uncovered.signum() > 0 ? "NO_RESERVE" : "BELOW_MIN";
    }
}
