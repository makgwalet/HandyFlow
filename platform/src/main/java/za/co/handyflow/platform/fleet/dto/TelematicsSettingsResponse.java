package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;

public record TelematicsSettingsResponse(
        int defaultSpeedLimitKph,
        int speedingToleranceKph,
        int speedingCooldownMinutes,
        int alertOverspeedKph,
        int idlingThresholdMinutes,
        int offlineAfterMinutes,
        boolean usingDefaults,
        Instant updatedAt
) {}