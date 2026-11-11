package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The rules of packing, with no I/O. */
public final class WhsePackingRules {

    private WhsePackingRules() {}

    public static final Set<String> TYPES = Set.of("BOX", "PALLET", "ENVELOPE", "CRATE", "OTHER");

    /** One order line as packing sees it: what will ship, and how much of that is already in packages. */
    public record LineState(String label, BigDecimal toShip, BigDecimal packed) {
        public BigDecimal remaining() { return toShip.subtract(packed).max(BigDecimal.ZERO); }
    }

    public static String type(String t) {
        String v = t == null || t.isBlank() ? "BOX" : t.trim().toUpperCase();
        if (!TYPES.contains(v)) throw new IllegalArgumentException("Package type must be one of " + String.join(", ", new java.util.TreeSet<>(TYPES)) + ".");
        return v;
    }

    public static Integer dimension(Integer mm, String name) {
        if (mm == null) return null;
        if (mm <= 0 || mm > 20_000) throw new IllegalArgumentException(name + " must be between 1 and 20000 mm.");
        return mm;
    }

    public static BigDecimal weight(BigDecimal kg, String name, boolean required) {
        if (kg == null) {
            if (required) throw new IllegalArgumentException("Enter the " + name + ".");
            return null;
        }
        if (kg.signum() <= 0 || kg.compareTo(new BigDecimal("100000")) > 0) throw new IllegalArgumentException("The " + name + " must be above zero and at most 100000 kg.");
        return kg;
    }

    /** How much may still be put in a package for a line: the quantity must be positive and no more than what is left to pack. */
    public static void checkAdd(LineState line, BigDecimal existingInThisPackage, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("Enter a quantity above zero.");
        if (qty.scale() > 3) throw new IllegalArgumentException("A quantity has at most 3 decimals.");
        // Setting a line's quantity in this package replaces what it had, so that is not counted twice.
        BigDecimal others = line.packed().subtract(existingInThisPackage == null ? BigDecimal.ZERO : existingInThisPackage);
        BigDecimal room = line.toShip().subtract(others);
        if (qty.compareTo(room) > 0) {
            throw new IllegalStateException(line.label() + ": only " + plain(room.max(BigDecimal.ZERO)) + " is left to pack (" + plain(line.toShip()) + " ships, "
                    + plain(others) + " already in other packages).");
        }
    }

    /** Closing needs something inside and a weighed gross weight above the tare. */
    public static void checkClose(int itemCount, BigDecimal grossKg, BigDecimal tareKg) {
        if (itemCount == 0) throw new IllegalStateException("The package is empty. Add what goes in it before closing.");
        weight(grossKg, "gross weight", true);
        if (grossKg.compareTo(tareKg == null ? BigDecimal.ZERO : tareKg) <= 0) throw new IllegalArgumentException("The gross weight must be more than the empty package weighs.");
    }

    /** A heads-up (not an error) when the weighed contents are far from what the item master says they should weigh. Null when fine or unknown. */
    public static String weightNote(BigDecimal grossKg, BigDecimal tareKg, BigDecimal expectedNetKg) {
        if (grossKg == null || expectedNetKg == null || expectedNetKg.signum() <= 0) return null;
        BigDecimal net = grossKg.subtract(tareKg == null ? BigDecimal.ZERO : tareKg);
        BigDecimal ratio = net.divide(expectedNetKg, 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.8")) < 0) return "Lighter than expected: contents weigh " + plain(net) + " kg, the item master says about " + plain(expectedNetKg) + " kg.";
        if (ratio.compareTo(new BigDecimal("1.3")) > 0) return "Heavier than expected: contents weigh " + plain(net) + " kg, the item master says about " + plain(expectedNetKg) + " kg.";
        return null;
    }

    /** What stops the order being marked packed. Empty when there are no packages at all (packing is optional) or everything is packed and closed. */
    public static List<String> notReady(int packages, int openPackages, List<LineState> lines) {
        List<String> problems = new ArrayList<>();
        if (packages == 0) return problems;
        if (openPackages > 0) problems.add(openPackages + (openPackages == 1 ? " package is" : " packages are") + " still open. Weigh and close " + (openPackages == 1 ? "it" : "them") + ".");
        for (LineState l : lines) {
            if (l.remaining().signum() > 0) problems.add(l.label() + ": " + plain(l.remaining()) + " not packed yet.");
            if (l.packed().compareTo(l.toShip()) > 0) problems.add(l.label() + ": more is packed (" + plain(l.packed()) + ") than ships (" + plain(l.toShip()) + ").");
        }
        return problems;
    }

    static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
