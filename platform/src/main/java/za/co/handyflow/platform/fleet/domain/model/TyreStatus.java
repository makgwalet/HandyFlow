package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Where a tyre is in its life. FITTED tyres are on a vehicle; SCRAPPED is terminal. */
public enum TyreStatus {
    IN_STOCK, FITTED, AWAITING_RETREAD, SCRAPPED;

    private static final Map<TyreStatus, Set<TyreStatus>> ALLOWED = new EnumMap<>(TyreStatus.class);

    static {
        ALLOWED.put(IN_STOCK, EnumSet.of(FITTED, SCRAPPED));
        ALLOWED.put(FITTED, EnumSet.of(IN_STOCK, AWAITING_RETREAD, SCRAPPED));
        ALLOWED.put(AWAITING_RETREAD, EnumSet.of(IN_STOCK, SCRAPPED));    // IN_STOCK = retread completed
        ALLOWED.put(SCRAPPED, EnumSet.noneOf(TyreStatus.class));
    }

    public boolean canTransitionTo(TyreStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}