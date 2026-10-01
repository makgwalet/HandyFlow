package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Splits one cost across several production targets by percentage (ADR-001, W1).
 * <p>
 * The parts always add up to the original amount EXACTLY, to the cent (largest-remainder apportionment), so a R100.00 cost split
 * 33.33 / 33.33 / 33.34 is R33.33 + R33.33 + R33.34, never R99.99 and never R100.01.
 * Pure and dependency-free so the arithmetic is unit tested without Spring or a database.
 */
public final class AgCostAllocation {

    private AgCostAllocation() {}

    public static final String CROP_CYCLE = "CROP_CYCLE";
    public static final String GROUP = "GROUP";
    public static final String ANIMAL = "ANIMAL";
    public static final String ENTERPRISE = "ENTERPRISE";
    public static final Set<String> TARGET_TYPES = Set.of(CROP_CYCLE, GROUP, ANIMAL, ENTERPRISE);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Percentages must total 100 within this tolerance (so 33.33 + 33.33 + 33.34 is accepted, 33.33 x 3 is not). */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.005");

    public record Share(String targetType, UUID targetId, BigDecimal percentage) {}

    public record Part(String targetType, UUID targetId, BigDecimal percentage, BigDecimal amount) {}

    /** @throws IllegalArgumentException when the amount or the shares are not a valid allocation */
    public static List<Part> split(BigDecimal amount, List<Share> shares) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (shares == null || shares.isEmpty()) throw new IllegalArgumentException("at least one allocation is required");

        Set<String> seen = new HashSet<>();
        BigDecimal totalPercent = BigDecimal.ZERO;
        for (Share s : shares) {
            if (s == null || s.targetType() == null || !TARGET_TYPES.contains(s.targetType())) {
                throw new IllegalArgumentException("targetType must be one of " + TARGET_TYPES);
            }
            if (s.targetId() == null) throw new IllegalArgumentException("targetId is required");
            if (s.percentage() == null || s.percentage().signum() <= 0 || s.percentage().compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("each percentage must be greater than 0 and at most 100");
            }
            if (!seen.add(s.targetType() + ":" + s.targetId())) {
                throw new IllegalArgumentException("the same target appears more than once; combine its percentages");
            }
            totalPercent = totalPercent.add(s.percentage());
        }
        if (totalPercent.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException("allocation percentages must total 100 (they total " + totalPercent.stripTrailingZeros().toPlainString() + ")");
        }

        // Largest-remainder apportionment in whole cents: round every share DOWN to the cent, then give the cents that are left over
        // to the shares with the biggest fractional parts (ties go to the earlier share). Shares are normalised by the actual
        // percentage total, so the tolerance above can never create or lose money, however large the amount.
        long totalCents = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        int n = shares.size();
        long[] cents = new long[n];
        BigDecimal[] fraction = new BigDecimal[n];
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = BigDecimal.valueOf(totalCents).multiply(shares.get(i).percentage()).divide(totalPercent, 12, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.DOWN);
            cents[i] = floor.longValueExact();
            fraction[i] = exact.subtract(floor);
            assigned += cents[i];
        }
        long leftover = totalCents - assigned;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((a, b) -> fraction[b].compareTo(fraction[a]) != 0 ? fraction[b].compareTo(fraction[a]) : Integer.compare(a, b));
        for (int k = 0; leftover > 0; k = (k + 1) % n, leftover--) cents[order.get(k)]++;
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < n; i++) amounts.add(BigDecimal.valueOf(cents[i], 2));

        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < shares.size(); i++) {
            Share s = shares.get(i);
            parts.add(new Part(s.targetType(), s.targetId(), s.percentage(), amounts.get(i)));
        }
        return parts;
    }
}
