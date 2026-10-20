package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleFuelAnalyticsResponse(
        UUID vehicleId,
        String registration,
        String fuelType,
        BigDecimal tankCapacityLitres,
        LocalDate from,
        LocalDate to,
        FuelSettingsResponse settings,
        FuelPeriodSummary summary,
        List<FuelSegmentResponse> segments,       // segments that ended in the period, newest first
        List<FuelIndicatorResponse> indicators    // newest first
) {}