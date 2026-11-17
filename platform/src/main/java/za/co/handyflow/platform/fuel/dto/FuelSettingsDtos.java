package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public final class FuelSettingsDtos {
    private FuelSettingsDtos() {}

    /** How shifts-by-date reports date a shift: the provider's closing time for imported shifts, or the system's; and the timezone that decides the day. */
    public record ReportBasis(boolean useProviderTimes, String timezone) {}

    /** The control-room contact a driver rings in an emergency. Both fields empty means none is set. */
    public record EmergencyContact(@jakarta.validation.constraints.Size(max = 100) String name, @jakarta.validation.constraints.Size(max = 30) String phone) {}

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
