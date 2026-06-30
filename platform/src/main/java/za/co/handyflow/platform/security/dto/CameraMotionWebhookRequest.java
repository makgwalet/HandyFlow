package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for the public camera motion webhook. webhookSecret proves the
 * call genuinely came from the registered camera/NVR — tenant and site are
 * derived from the matched Camera row, never trusted from the request body.
 */
public record CameraMotionWebhookRequest(
        @NotNull  UUID   cameraId,
        @NotBlank String webhookSecret,
        String severity,        // optional override — LOW | MEDIUM | HIGH | CRITICAL, defaults MEDIUM
        String description,
        String rawPayload       // verbatim vendor payload, stored for reference
) {}
