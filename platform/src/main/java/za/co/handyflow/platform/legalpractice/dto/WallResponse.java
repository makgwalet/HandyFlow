package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.UUID;

public record WallResponse(UUID id, String scope, UUID matterId, UUID clientId, String targetLabel, UUID userId, String userName, String reason,
                           String createdByName, Instant createdAt, boolean active, String liftedByName, Instant liftedAt, String liftReason) {
}
