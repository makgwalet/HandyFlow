package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** roomId is optional: a booking with no room is allowed. */
public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        UUID practitionerId,
        @NotNull Instant scheduledAt,
        Integer durationMinutes,
        String appointmentType,
        String reason,
        UUID roomId
) {
    /** The booking without a room. */
    public CreateAppointmentRequest(UUID patientId, UUID practitionerId, Instant scheduledAt,
                                    Integer durationMinutes, String appointmentType, String reason) {
        this(patientId, practitionerId, scheduledAt, durationMinutes, appointmentType, reason, null);
    }
}
