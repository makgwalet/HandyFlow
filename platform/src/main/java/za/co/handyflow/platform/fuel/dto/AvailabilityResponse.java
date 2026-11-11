package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Available to promise = physical stock - live reservations - safety stock (never below zero).
 * Reserving fuel does not change the physical balance.
 */
public record AvailabilityResponse(
        List<TankLine> tanks, List<DepotLine> depots
) {
    public record TankLine(UUID tankId, String tankName, String fuelType, UUID depotId, BigDecimal physical,
                           BigDecimal reserved, BigDecimal safetyStock, BigDecimal availableToPromise,
                           BigDecimal ledgerBalance, boolean ledgerMatches) {}

    public record DepotLine(UUID depotId, String depotName, String fuelType, BigDecimal physical,
                            BigDecimal reserved, BigDecimal safetyStock, BigDecimal availableToPromise) {}
}
