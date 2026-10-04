package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Gross-margin rules (ADR-001, W5). Gross margin is revenue minus DIRECT production costs and nothing more; Agriculture never computes net profit.
 * Pure and dependency-free so the arithmetic and the lifecycle rules are unit tested without Spring or a database.
 */
public final class AgProfitabilityRules {

    private AgProfitabilityRules() {}

    public static final String COMPLETE = "COMPLETE";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    /** An animal flagged as breeding stock: shown apart from the production margins, with its purchase price (capital) left out. */
    public static final String BREEDING_STOCK = "BREEDING_STOCK";

    /**
     * Whether a unit's life is over, so its margin is final. A harvested, failed or abandoned crop cycle, a closed group, and an animal that was
     * sold, died, was culled or was transferred out are COMPLETE. Everything else, and every enterprise, is still running: only the margin to date
     * is known and unsold stock is not valued (ADR-001 decision 8).
     */
    public static String stateOf(String targetType, String status) {
        if (targetType == null || status == null) return IN_PROGRESS;
        return switch (targetType) {
            case "CROP_CYCLE" -> isOneOf(status, "HARVESTED", "FAILED", "ABANDONED") ? COMPLETE : IN_PROGRESS;
            case "GROUP" -> "CLOSED".equals(status) ? COMPLETE : IN_PROGRESS;
            case "ANIMAL" -> isOneOf(status, "SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT") ? COMPLETE : IN_PROGRESS;
            default -> IN_PROGRESS;                                          // an enterprise is ongoing
        };
    }

    /** Revenue minus direct cost, to the cent. */
    public static BigDecimal margin(BigDecimal revenue, BigDecimal directCost) {
        return revenue.subtract(directCost).setScale(2, RoundingMode.HALF_UP);
    }

    /** The margin as a percentage of revenue to one decimal; null when there is no revenue (there is nothing to be a percentage of). */
    public static BigDecimal marginPercent(BigDecimal revenue, BigDecimal margin) {
        if (revenue == null || revenue.signum() <= 0) return null;
        return margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }

    /** Whether a finished unit would normally have been SOLD, so having no sales attributed to it is worth saying. */
    static boolean expectsSales(String targetType, String status) {
        return ("CROP_CYCLE".equals(targetType) && "HARVESTED".equals(status))
                || ("GROUP".equals(targetType) && "CLOSED".equals(status))
                || ("ANIMAL".equals(targetType) && "SOLD".equals(status));
    }

    /** Plain-language reasons this unit's margin should be read with care. */
    public static List<String> caveats(String targetType, String status, String acquisitionType, BigDecimal revenue) {
        return caveats(targetType, status, acquisitionType, revenue, false);
    }

    /**
     * As above, for a unit that may be breeding stock. Breeding stock is not expected to be sold, so it gets no "no sales" warning; it gets the
     * explanation of what its row does and does not include instead.
     */
    public static List<String> caveats(String targetType, String status, String acquisitionType, BigDecimal revenue, boolean breedingStock) {
        List<String> out = new ArrayList<>();
        if (breedingStock) {
            out.add("Breeding stock: its purchase price is capital, so it isn't counted here. Only its running costs and any sales are shown, and this isn't a production margin.");
            return out;
        }
        if (expectsSales(targetType, status) && (revenue == null || revenue.signum() <= 0)) {
            out.add("No sales are attributed to this yet, so its margin shows a loss until they are.");
        }
        if ("GROUP".equals(targetType) && "PURCHASED".equals(acquisitionType)) {
            out.add("The purchase price of this batch isn't recorded, so its costs are understated and its margin overstated.");
        }
        return out;
    }

    private static boolean isOneOf(String s, String... options) {
        for (String o : options) if (o.equals(s)) return true;
        return false;
    }
}
