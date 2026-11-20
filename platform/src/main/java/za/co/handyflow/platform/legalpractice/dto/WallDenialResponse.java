package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.UUID;

public record WallDenialResponse(UUID id, UUID userId, String userName, UUID matterId, String matterLabel, UUID clientId, UUID wallId, Instant deniedAt) {
}
