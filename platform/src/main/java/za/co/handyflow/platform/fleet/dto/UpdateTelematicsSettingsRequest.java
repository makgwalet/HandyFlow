package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateTelematicsSettingsRequest(
        @Min(20) @Max(200) int defaultSpeedLimitKph,
        @Min(0) @Max(30) int speedingToleranceKph,
        @Min(1) @Max(120) int speedingCooldownMinutes,
        @Min(5) @Max(100) int alertOverspeedKph,
        @Min(1) @Max(240) int idlingThresholdMinutes,
        @Min(5) @Max(1440) int offlineAfterMinutes
) {}