package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        UUID practitionerId,
        @NotNull Instant scheduledAt,
        Integer durationMinutes,
        String appointmentType,
        String reason
) {}