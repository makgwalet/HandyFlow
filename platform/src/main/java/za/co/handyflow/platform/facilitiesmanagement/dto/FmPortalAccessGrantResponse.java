package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmPortalAccessGrantResponse(
        UUID id,
        UUID clientId,
        String inviteEmail,
        String status,
        Instant acceptedAt,
        Instant invitedAt
) {}
