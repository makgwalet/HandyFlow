package za.co.handyflow.platform.security.dto;

import java.util.UUID;

/**
 * Returned exactly once — when a webhook secret is generated or rotated.
 * The supervisor must copy it into the camera/NVR's webhook configuration
 * immediately; it cannot be retrieved again afterward (same pattern as the
 * guard PIN reset flow in Phase 1.5).
 */
public record CameraWebhookSecretResponse(
        UUID   cameraId,
        String webhookSecret
) {}
