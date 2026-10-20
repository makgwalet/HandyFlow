package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Install a battery. {@code position} defaults to "1" (use "2" for a second battery). Odometer rules match tyre fitting. */
public record InstallBatteryRequest(
        @NotNull UUID vehicleId,
        @Size(max = 10) String position,
        @PositiveOrZero Integer odometer,
        Instant installedAt,
        UUID workOrderId
) {}