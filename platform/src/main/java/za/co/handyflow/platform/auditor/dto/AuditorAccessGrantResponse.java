package za.co.handyflow.platform.auditor.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditorAccessGrantResponse(
        UUID id,
        String inviteEmail,
        String status,
        Instant invitedAt,
        Instant acceptedAt,
        Instant revokedAt
) {}