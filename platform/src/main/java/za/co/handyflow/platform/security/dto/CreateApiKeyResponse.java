package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned once at key creation — the rawKey is never stored and cannot
 * be retrieved again. The caller must copy it immediately.
 */
public record CreateApiKeyResponse(
        UUID    id,
        String  name,
        String  rawKey,       // full key — shown once only
        String  keyPrefix,    // e.g. "hf_live_a3" — safe to display in the UI later
        boolean readOnly,
        Instant expiresAt,
        Instant createdAt
) {}
