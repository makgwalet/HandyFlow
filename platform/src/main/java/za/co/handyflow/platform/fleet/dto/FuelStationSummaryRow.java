package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;

/** Spend at one station for one fuel type, compared with what the tenant pays on average for that fuel. */
public record FuelStationSummaryRow(
        String station,
        String fuelType,
        int fillups,
        BigDecimal litres,
        BigDecimal totalSpend,
        BigDecimal avgPricePerLitre,
        BigDecimal tenantAvgPricePerLitre,     // same fuel type, all stations
        Double premiumPercent                  // positive = dearer than the tenant average
) {}