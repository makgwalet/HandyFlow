package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** What is wrong with a line of an inbound shipment. Pure rules, no I/O. */
public final class WhseReceivingRules {

    public static final String SHORT = "SHORT";
    public static final String OVER = "OVER";
    public static final String DAMAGED = "DAMAGED";
    public static final String UNEXPECTED_ITEM = "UNEXPECTED_ITEM";
    public static final String HELD_FOR_INSPECTION = "HELD_FOR_INSPECTION";

    private WhseReceivingRules() {}

    /**
     * @param received     everything physically received on the line, good, damaged and held
     * @param damaged      damaged on arrival plus held stock that failed inspection
     * @param heldPending  held stock that has not been inspected yet
     * @param unexpected   the item was not on the advice
     * @param finalised    the delivery is complete (received in full or closed). Until then a shortfall is only outstanding, not short.
     */
    public static List<String> classify(BigDecimal expected, BigDecimal received, BigDecimal damaged, BigDecimal heldPending,
                                        boolean unexpected, boolean finalised) {
        List<String> out = new ArrayList<>();
        if (unexpected) {
            if (received.signum() > 0) out.add(UNEXPECTED_ITEM);
        } else {
            if (finalised && received.compareTo(expected) < 0) out.add(SHORT);
            if (received.compareTo(expected) > 0) out.add(OVER);
        }
        if (damaged.signum() > 0) out.add(DAMAGED);
        if (heldPending.signum() > 0) out.add(HELD_FOR_INSPECTION);
        return out;
    }

    /** Held-for-inspection is work to do, not a fault in the delivery, so it does not need a sign-off note. */
    public static boolean needsSignOff(List<String> types) {
        return types.stream().anyMatch(t -> !HELD_FOR_INSPECTION.equals(t));
    }

    public static BigDecimal shortQty(BigDecimal expected, BigDecimal received) {
        BigDecimal d = expected.subtract(received);
        return d.signum() > 0 ? d : BigDecimal.ZERO;
    }

    public static BigDecimal overQty(BigDecimal expected, BigDecimal received) {
        BigDecimal d = received.subtract(expected);
        return d.signum() > 0 ? d : BigDecimal.ZERO;
    }
}
