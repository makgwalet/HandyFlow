package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Move an appointment. A null length or practitioner leaves it as it is. */
public record RescheduleRequest(
        @NotNull Instant scheduledAt,
        Integer durationMinutes,
        UUID practitionerId
) {}
