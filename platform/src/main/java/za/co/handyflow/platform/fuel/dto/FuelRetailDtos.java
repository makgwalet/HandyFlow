package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** D01: tank-to-pump reconciliation. */
public final class FuelRetailDtos {
    private FuelRetailDtos() {}

    /** One line of what moved between the two dips, grouped by what caused it. Litres are signed: negative left the tank. */
    public record MovementLine(String label, BigDecimal litres, int count) {}

    /**
     * What should be in the tank at the closing dip given the opening dip and everything the system recorded in between,
     * against what the dip actually found; and whether the pump meters agree with the ledger's forecourt sales.
     * {@code ready} is false (with {@code message}) when there are not two dips to compare.
     */
    public record TankReconciliation(boolean ready, String message, UUID tankId, String tankName, String fuelType,
                                     Instant fromDipAt, BigDecimal fromDipLitres, Instant toDipAt, BigDecimal toDipLitres,
                                     List<MovementLine> movements, BigDecimal expectedLitres, BigDecimal varianceLitres,
                                     BigDecimal throughputLitres, BigDecimal variancePct,
                                     BigDecimal meterLitres, BigDecimal ledgerForecourtLitres, BigDecimal meterVsLedgerLitres,
                                     BigDecimal tolerancePct, boolean outsideTolerance) {
        public static TankReconciliation notReady(UUID tankId, String tankName, String fuelType, String message) {
            return new TankReconciliation(false, message, tankId, tankName, fuelType, null, null, null, null, List.of(), null, null, null, null,
                    null, null, null, null, false);
        }
    }
}
