package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** First-expiry-first-out planning over the lots that hold available stock. Pure, no I/O. */
public final class WhseFefo {

    private WhseFefo() {}

    public record Candidate(UUID inventoryId, UUID lotId, String lotNumber, UUID locationId, LocalDate expiry, boolean blocked, BigDecimal available) {}
    public record Pick(Candidate source, BigDecimal qty) {}

    /**
     * Takes the required quantity from the lots with the earliest expiry first (lots without an expiry date last), skipping blocked lots,
     * expired lots and lots with less than {@code minShelfLifeDays} left. Ties go by lot number then position, so a plan is repeatable.
     *
     * @throws IllegalStateException when the sellable stock does not cover the quantity; the message says what was held back and why
     */
    public static List<Pick> plan(List<Candidate> candidates, BigDecimal required, LocalDate today, Integer minShelfLifeDays) {
        if (required == null || required.signum() <= 0) throw new IllegalArgumentException("The quantity to allocate must be above zero.");
        BigDecimal blockedQty = BigDecimal.ZERO, shortDatedQty = BigDecimal.ZERO;
        List<Candidate> usable = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.available() == null || c.available().signum() <= 0) continue;
            if (c.blocked()) { blockedQty = blockedQty.add(c.available()); continue; }
            if (!WhseLotRules.sellable(c.expiry(), today, minShelfLifeDays)) { shortDatedQty = shortDatedQty.add(c.available()); continue; }
            usable.add(c);
        }
        usable.sort(Comparator
                .comparing(Candidate::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.lotNumber() == null ? "" : c.lotNumber())
                .thenComparing(c -> c.inventoryId().toString()));

        List<Pick> picks = new ArrayList<>();
        BigDecimal left = required;
        for (Candidate c : usable) {
            if (left.signum() == 0) break;
            BigDecimal take = c.available().min(left);
            picks.add(new Pick(c, take));
            left = left.subtract(take);
        }
        if (left.signum() > 0) {
            BigDecimal got = required.subtract(left);
            StringBuilder msg = new StringBuilder("Not enough sellable stock: ").append(plain(required)).append(" needed, ").append(plain(got)).append(" available");
            if (blockedQty.signum() > 0) msg.append("; ").append(plain(blockedQty)).append(" is in blocked lots");
            if (shortDatedQty.signum() > 0) msg.append("; ").append(plain(shortDatedQty)).append(" is expired or has too little shelf life left");
            throw new IllegalStateException(msg.append('.').toString());
        }
        return picks;
    }

    /** As {@link #plan}, but takes what it can: a shortage is not an error. Returns the picks (possibly none); the caller works out the rest. */
    public static List<Pick> planUpTo(List<Candidate> candidates, BigDecimal required, LocalDate today, Integer minShelfLifeDays) {
        if (required == null || required.signum() <= 0) throw new IllegalArgumentException("The quantity to allocate must be above zero.");
        List<Candidate> usable = new ArrayList<>(candidates.stream()
                .filter(c -> c.available() != null && c.available().signum() > 0 && !c.blocked() && WhseLotRules.sellable(c.expiry(), today, minShelfLifeDays)).toList());
        usable.sort(Comparator
                .comparing(Candidate::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.lotNumber() == null ? "" : c.lotNumber())
                .thenComparing(c -> c.inventoryId().toString()));
        List<Pick> picks = new ArrayList<>();
        BigDecimal left = required;
        for (Candidate c : usable) {
            if (left.signum() == 0) break;
            BigDecimal take = c.available().min(left);
            picks.add(new Pick(c, take));
            left = left.subtract(take);
        }
        return picks;
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
