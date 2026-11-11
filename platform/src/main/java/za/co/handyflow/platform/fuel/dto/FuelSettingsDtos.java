package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public final class FuelSettingsDtos {
    private FuelSettingsDtos() {}

    public record SettingsRequest(
            @DecimalMin("0") BigDecimal driverCostPerHour,
            @DecimalMin("0") BigDecimal vehicleCostPerKm,
            @DecimalMin("0") BigDecimal fixedCostPerDelivery,
            @DecimalMin("0") @DecimalMax("100") BigDecimal overheadPctOfRevenue,
            @DecimalMin("0.1") @DecimalMax("24") BigDecimal defaultTripHours,
            @DecimalMin("0") @DecimalMax("10") BigDecimal poOverTolerancePct) {}

    public record SettingsResponse(BigDecimal driverCostPerHour, BigDecimal vehicleCostPerKm, BigDecimal fixedCostPerDelivery,
                                   BigDecimal overheadPctOfRevenue, BigDecimal defaultTripHours, BigDecimal poOverTolerancePct,
                                   boolean ratesConfigured) {}
}
