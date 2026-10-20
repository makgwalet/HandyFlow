package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** FAULTY = removed as failed and awaiting a warranty claim or disposal. SCRAPPED is terminal. */
public enum BatteryStatus {
    IN_STOCK, INSTALLED, FAULTY, SCRAPPED;

    private static final Map<BatteryStatus, Set<BatteryStatus>> ALLOWED = new EnumMap<>(BatteryStatus.class);

    static {
        ALLOWED.put(IN_STOCK, EnumSet.of(INSTALLED, SCRAPPED));
        ALLOWED.put(INSTALLED, EnumSet.of(IN_STOCK, FAULTY, SCRAPPED));
        ALLOWED.put(FAULTY, EnumSet.of(IN_STOCK, SCRAPPED));              // IN_STOCK = recharged / repaired
        ALLOWED.put(SCRAPPED, EnumSet.noneOf(BatteryStatus.class));
    }

    public boolean canTransitionTo(BatteryStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}