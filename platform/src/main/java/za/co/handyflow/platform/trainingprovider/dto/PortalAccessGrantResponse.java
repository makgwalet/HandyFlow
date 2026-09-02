package za.co.handyflow.platform.trainingprovider.dto;

import java.time.Instant;
import java.util.UUID;

public record PortalAccessGrantResponse(
        UUID id,
        UUID clientId,
        String inviteEmail,
        String status,
        Instant acceptedAt,
        Instant createdAt
) {}
