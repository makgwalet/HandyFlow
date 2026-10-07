package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class TimeOffDtos {
    private TimeOffDtos() {}

    public record CreateTimeOffRequest(@NotNull Instant startsAt, @NotNull Instant endsAt, String reason) {}

    public record TimeOffResponse(UUID id, UUID practitionerId, Instant startsAt, Instant endsAt, String reason, Instant createdAt) {}
}
