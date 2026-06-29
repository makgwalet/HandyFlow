package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;

import java.util.Map;
import java.util.UUID;

public record CreateRotationPatternRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 120) String name,
        @NotNull String patternType,           // FIXED_DAYS_ON_OFF | ALTERNATING_DAY_NIGHT | WEEKLY_FIXED | CUSTOM
        @NotNull Map<String, Object> cycleDefinition,
        @Min(4) @Max(24) int shiftLengthHours
) {}