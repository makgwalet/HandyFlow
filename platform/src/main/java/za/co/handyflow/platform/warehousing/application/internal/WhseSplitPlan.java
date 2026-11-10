package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Splits a quantity of an item that is not lot tracked across the locations that hold it. Pure, no I/O. */
public final class WhseSplitPlan {

    private WhseSplitPlan() {}

    public record Source(UUID inventoryId, UUID locationId, Integer pickSequence, BigDecimal available) {}
    public record Piece(Source source, BigDecimal qty) {}
    public record Result(List<Piece> pieces, BigDecimal unallocated) {
        public BigDecimal allocated() { return pieces.stream().map(Piece::qty).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    /**
     * One location that covers the whole quantity wins (the earliest in pick order). Otherwise the biggest positions are used first, so the
     * picker makes as few stops as possible. What the stock cannot cover is returned as {@code unallocated}.
     */
    public static Result plan(List<Source> sources, BigDecimal required) {
        if (required == null || required.signum() <= 0) throw new IllegalArgumentException("The quantity to allocate must be above zero.");
        List<Source> usable = new ArrayList<>(sources.stream().filter(s -> s.available() != null && s.available().signum() > 0).toList());
        Comparator<Source> pickOrder = Comparator.comparing(Source::pickSequence, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.inventoryId().toString());
        List<Source> covering = usable.stream().filter(s -> s.available().compareTo(required) >= 0).sorted(pickOrder).toList();
        if (!covering.isEmpty()) return new Result(List.of(new Piece(covering.get(0), required)), BigDecimal.ZERO);
        usable.sort(Comparator.comparing(Source::available).reversed().thenComparing(pickOrder));
        List<Piece> pieces = new ArrayList<>();
        BigDecimal left = required;
        for (Source s : usable) {
            if (left.signum() == 0) break;
            BigDecimal take = s.available().min(left);
            pieces.add(new Piece(s, take));
            left = left.subtract(take);
        }
        return new Result(pieces, left);
    }
}
