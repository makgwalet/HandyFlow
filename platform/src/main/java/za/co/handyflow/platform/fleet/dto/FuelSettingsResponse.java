package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;

public record FuelSettingsResponse(
        BigDecimal expectedLitresPer100Km,   // null = using the rolling baseline
        int tolerancePercent,                // effective tolerance
        boolean usingDefaultTolerance
) {}