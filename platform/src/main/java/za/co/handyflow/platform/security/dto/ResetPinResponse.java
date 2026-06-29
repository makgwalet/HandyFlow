package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record ResetPinResponse(
        UUID guardId,
        String temporaryPin,    // plaintext, sent once — NOT stored
        Instant pinExpiresAt    // guard must set new PIN before this
) {}