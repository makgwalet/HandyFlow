package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Weights must total 100 (checked on save). Band thresholds must descend: excellent > good > fair > 0. */
public record UpdateDriverScoreSettingsRequest(
        @Min(0) @Max(100) int weightSafety,
        @Min(0) @Max(100) int weightInspection,
        @Min(0) @Max(100) int weightAccidents,
        @Min(0) @Max(100) int weightFuel,
        @Min(0) @Max(100) int weightTrips,
        @Min(0) @Max(100) int weightCompliance,
        @Min(7) @Max(730) int lookbackDays,
        @Min(1) @Max(100) int excellentThreshold,
        @Min(1) @Max(100) int goodThreshold,
        @Min(1) @Max(100) int fairThreshold
) {}