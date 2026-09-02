package za.co.handyflow.platform.bookkeeping.dto;

import java.time.Instant;
import java.util.UUID;

public record BkPortalAccessGrantResponse(
        UUID id,
        UUID clientId,
        String inviteEmail,
        String status,
        Instant acceptedAt,
        Instant invitedAt
) {}
