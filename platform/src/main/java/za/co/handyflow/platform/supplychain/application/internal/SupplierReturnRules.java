package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure rules for supplier returns and claims.
 * <pre>
 * RETURN (goods go back):  DRAFT -> SUBMITTED -> APPROVED -> DISPATCHED -> CREDITED
 * CLAIM  (credit only):    DRAFT -> SUBMITTED -> APPROVED -> CREDITED
 * any open step -> REJECTED (by the approver, with a reason) or CANCELLED (by the requester, before dispatch)
 * </pre>
 * Dispatch takes the stock out of the location it was received into; a claim never touches stock.
 */
public final class SupplierReturnRules {

    private SupplierReturnRules() {}

    public static final Set<String> KINDS = Set.of("RETURN", "CLAIM");
    public static final Set<String> REASONS = Set.of("DAMAGED", "WRONG_ITEM", "SHORT_SUPPLY", "QUALITY", "OVERCHARGE", "OTHER");

    public record Line(BigDecimal qty, BigDecimal unitCost) {}

    /** Whether {@code from -> to} is a legal move for this kind. */
    public static boolean canMove(String kind, String from, String to) {
        return switch (from) {
            case "DRAFT" -> Set.of("SUBMITTED", "CANCELLED").contains(to);
            case "SUBMITTED" -> Set.of("APPROVED", "REJECTED", "CANCELLED").contains(to);
            case "APPROVED" -> "RETURN".equals(kind)
                    ? Set.of("DISPATCHED", "CANCELLED").contains(to)
                    : Set.of("CREDITED", "CANCELLED").contains(to);
            case "DISPATCHED" -> "CREDITED".equals(to);
            default -> false;                     // CREDITED, REJECTED, CANCELLED are final
        };
    }

    /** Null when the lines are acceptable, else the problem. */
    public static String lineProblem(List<Line> lines) {
        if (lines == null || lines.isEmpty()) return "A return or claim needs at least one line";
        for (Line l : lines) {
            if (l.qty() == null || l.qty().signum() <= 0) return "Every line needs a quantity above zero";
            if (l.unitCost() == null || l.unitCost().signum() < 0) return "Every line needs a unit cost of zero or more";
        }
        return null;
    }

    public static BigDecimal expectedCredit(List<Line> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (Line l : lines) total = total.add(l.qty().multiply(l.unitCost()));
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * A receipt line can only come back once: accepted quantity, less what other live returns/claims already take.
     * Returns the message for the first over-return, or null.
     */
    public static String overReturn(Map<String, BigDecimal> requestedByLine, Map<String, BigDecimal> acceptedByLine,
                                    Map<String, BigDecimal> alreadyReturnedByLine) {
        for (var e : requestedByLine.entrySet()) {
            BigDecimal accepted = acceptedByLine.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal already = alreadyReturnedByLine.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal room = accepted.subtract(already);
            if (e.getValue().compareTo(room) > 0)
                return "Only " + room.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " of that receipt line can still be returned or claimed ("
                        + accepted.stripTrailingZeros().toPlainString() + " accepted, " + already.stripTrailingZeros().toPlainString() + " already on other returns)";
        }
        return null;
    }

    /** A credit note more than 5 cents above what the claim expected is refused: it must be corrected or the claim amended first. */
    public static boolean creditExceedsExpected(BigDecimal expected, BigDecimal credit) {
        if (credit == null) return false;
        return credit.compareTo(expected.add(new BigDecimal("0.05"))) > 0;
    }
}
