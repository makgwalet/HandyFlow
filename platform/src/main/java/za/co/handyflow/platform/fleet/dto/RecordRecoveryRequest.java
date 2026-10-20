package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.RecoveryStatus;

import java.time.Instant;

public record RecordRecoveryRequest(
        @NotNull RecoveryStatus recoveryStatus,   // RECOVERED | RECOVERED_DAMAGED
        @NotNull Instant recoveredAt,
        @Size(max = 500) String location,
        String notes,
        @PositiveOrZero Integer odometer          // optional reading at recovery; cannot be lower than the vehicle's current one
) {}