package za.co.handyflow.platform.security.dto;
import java.time.Instant; import java.util.UUID;
public record ShiftResponse(
        UUID id, UUID siteId, UUID guardId,
        Instant startAt, Instant endAt, String status,
        String notes, Instant createdAt
) {}
