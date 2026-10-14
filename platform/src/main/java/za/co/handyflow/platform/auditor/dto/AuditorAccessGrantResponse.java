package za.co.handyflow.platform.auditor.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditorAccessGrantResponse(
        UUID id,
        String inviteEmail,
        String status,
        Instant invitedAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant expiresAt,
        String scopeNote,
        /** ACTIVE grants whose date has passed show as EXPIRED here so the screen cannot mislead. */
        boolean expired
) {}
