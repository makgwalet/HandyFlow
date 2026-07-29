package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Days until empty at current usage" forecast for one tank — the audit's
 * own framing: "given consumption rate (dispatches) and current level are
 * both tracked, a simple projection would be a natural, low-effort addition
 * directly useful for reorder timing."
 * <p>
 * hasSufficientData is false when there's no dispatch activity for this
 * tank in the lookback window — avgDailyLitres/daysUntilEmpty/
 * projectedEmptyDate are all null/zero in that case rather than a
 * misleadingly precise number computed from no real usage signal (a tank
 * that's simply not moved recently shouldn't report "infinite days until
 * empty" as if that were a meaningful forecast).
 */
public record TankUtilizationForecastResponse(
        UUID tankId,
        String tankName,
        BigDecimal avgDailyLitres,
        Integer daysUntilEmpty,
        LocalDate projectedEmptyDate,
        int lookbackDays,
        boolean hasSufficientData
) {
}