package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientNoteResponse(
        UUID id,
        boolean pinned,
        String note,
        Instant createdAt
) {
}
