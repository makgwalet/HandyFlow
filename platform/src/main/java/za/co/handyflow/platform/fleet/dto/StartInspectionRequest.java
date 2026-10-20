package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record StartInspectionRequest(
        @NotNull UUID templateId,
        @PositiveOrZero Integer odometer,   // cannot be lower than the vehicle's current reading; raises it if higher
        String inspectorName,
        UUID driverId
) {}