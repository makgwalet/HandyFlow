package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreatePatrolRouteRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 120) String name,
        @Min(15) @Max(480) int intervalMinutes,
        @Min(5)  @Max(60)  int toleranceMinutes
) {}