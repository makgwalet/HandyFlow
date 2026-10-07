package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Move an appointment. A null length, practitioner or room leaves it as it is; clearRoom = true takes the room off. */
public record RescheduleRequest(
        @NotNull Instant scheduledAt,
        Integer durationMinutes,
        UUID practitionerId,
        UUID roomId,
        Boolean clearRoom
) {
    public RescheduleRequest(Instant scheduledAt, Integer durationMinutes, UUID practitionerId, UUID roomId) {
        this(scheduledAt, durationMinutes, practitionerId, roomId, null);
    }

    public boolean wantsRoomCleared() {
        return Boolean.TRUE.equals(clearRoom);
    }

    public RescheduleRequest(Instant scheduledAt, Integer durationMinutes, UUID practitionerId) {
        this(scheduledAt, durationMinutes, practitionerId, null, null);
    }
}
