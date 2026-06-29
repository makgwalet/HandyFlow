package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.*;

import java.util.Map;

public record UpdateRotationPatternRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Map<String, Object> cycleDefinition,
        @Min(4) @Max(24) int shiftLengthHours
) {}