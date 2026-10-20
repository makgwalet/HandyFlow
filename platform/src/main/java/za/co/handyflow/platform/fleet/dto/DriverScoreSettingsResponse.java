package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;

public record DriverScoreSettingsResponse(
        int weightSafety,
        int weightInspection,
        int weightAccidents,
        int weightFuel,
        int weightTrips,
        int weightCompliance,
        int lookbackDays,
        int excellentThreshold,
        int goodThreshold,
        int fairThreshold,
        boolean usingDefaults,      // true when the tenant has never saved settings
        Instant updatedAt
) {}