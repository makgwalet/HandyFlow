// fuel/dto/FuelMarginLineResponse.java

package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// FIX (fuel cost/margin engine, agreed design): one line per delivery or
// dispatch in the report period. revenue/margin/marginPercent are null
// for internal (non-customer-billed) dispatches — there's no sale, so
// "margin" doesn't apply, only cost. costPerLitreAtSale/cost are null
// for transactions that predate this feature (not backfilled, per the
// agreed design) or where the tank had no WAC yet at that point.
public record FuelMarginLineResponse(
        String sourceType,          // "DELIVERY" | "DISPATCH"
        UUID id,
        UUID tankId,
        Instant occurredAt,
        BigDecimal litres,
        BigDecimal pricePerLitre,       // null if not customer-billed
        BigDecimal costPerLitreAtSale,  // null if predates this feature or tank had no WAC yet
        BigDecimal revenue,             // null if not customer-billed
        BigDecimal cost,                // null only if costPerLitreAtSale is null
        BigDecimal margin,              // null unless both revenue and cost are present
        BigDecimal marginPercent,       // null unless margin is present and revenue > 0
        String recipientLabel
) {}
