package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public final class AddendumDtos {
    private AddendumDtos() {}

    public record AddAddendumRequest(String text) {}

    public record AddendumResponse(UUID id, UUID consultationId, UUID authorUserId, String text, Instant createdAt) {}
}
