package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Rules for a package (bundle): what it may contain and what it is worth. Pure, no database. */
public final class BundleRules {

    private BundleRules() {}

    public static final int MAX_COMPONENTS = 30;
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("9999.999");

    /** One line the caller asked for. */
    public record Line(UUID itemId, BigDecimal quantity) {}

    /** What a component item looks like to the rules. */
    public record Component(UUID id, CatalogueItemType type, CatalogueItemStatus status, boolean deleted, boolean hasVariants) {}

    /** Quantities are positive, at most three decimals, and at most 9999.999. */
    public static BigDecimal quantity(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new IllegalArgumentException("Each quantity must be more than zero.");
        if (q.stripTrailingZeros().scale() > 3) throw new IllegalArgumentException("A quantity can have at most 3 decimals.");
        BigDecimal s = q.setScale(3, RoundingMode.UNNECESSARY);
        if (s.compareTo(MAX_QUANTITY) > 0) throw new IllegalArgumentException("A quantity can be at most 9999.999.");
        return s;
    }

    /**
     * Checks the whole list. The bundle must be a package item; lines must be unique, within the limit, and refer to
     * live items that are not packages themselves and do not have variants (choose a variant instead).
     * Returns the cleaned lines in the order given.
     */
    public static List<Line> validate(UUID bundleId, CatalogueItemType bundleType, List<Line> lines, Map<UUID, Component> found) {
        if (bundleType != CatalogueItemType.PACKAGE) throw new IllegalArgumentException("Only an item of type Package can have components.");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Add at least one component.");
        if (lines.size() > MAX_COMPONENTS) throw new IllegalArgumentException("A package can have at most " + MAX_COMPONENTS + " components.");
        Set<UUID> seen = new HashSet<>();
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            if (l == null || l.itemId() == null) throw new IllegalArgumentException("Each component needs an item.");
            if (l.itemId().equals(bundleId)) throw new IllegalArgumentException("A package cannot contain itself.");
            if (!seen.add(l.itemId())) throw new IllegalArgumentException("Each item can appear only once. Raise its quantity instead.");
            Component c = found.get(l.itemId());
            if (c == null || c.deleted()) throw new IllegalArgumentException("A component no longer exists. Remove it and try again.");
            if (c.type() == CatalogueItemType.PACKAGE) throw new IllegalArgumentException("A package cannot contain another package.");
            if (c.hasVariants()) throw new IllegalArgumentException("Choose a variant of that item, not the item itself.");
            if (c.status() == CatalogueItemStatus.ARCHIVED) throw new IllegalArgumentException("An archived item cannot be a component.");
            out.add(new Line(l.itemId(), quantity(l.quantity())));
        }
        return out;
    }

    /** Sum of price x quantity; a component without a price counts as zero and is reported by {@link #unpriced}. */
    public static BigDecimal total(List<BigDecimal> prices, List<BigDecimal> quantities) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i) != null) sum = sum.add(prices.get(i).multiply(quantities.get(i)));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    public static int unpriced(List<BigDecimal> prices) { return (int) prices.stream().filter(Objects::isNull).count(); }

    /**
     * What the customer saves against buying the parts separately: positive when the package is cheaper. Null when the
     * package or any part has no price, since the comparison would mislead.
     */
    public static BigDecimal saving(BigDecimal bundlePrice, BigDecimal partsTotal, int unpriced) {
        if (bundlePrice == null || unpriced > 0) return null;
        return partsTotal.subtract(bundlePrice).setScale(2, RoundingMode.HALF_UP);
    }
}
