package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Guard enrollment request — supervisor sets PIN, records face embedding and device.
 *
 * Called by the admin web app when a supervisor onboards a new guard.
 * The supervisor types the initial PIN on their own trusted device.
 * The Shield app sends the face embedding from the guard's device separately
 * (or as part of the same request if both devices are present).
 *
 * pinExpiryDays: null = use tenant policy default (90 days).
 */
public record GuardEnrollRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
        String pin,

        String faceEmbeddingBase64,   // Base64 float vector from on-device face model; null = skip
        String deviceHardwareId,      // hardware ID of the guard's assigned device; null = skip

        Integer pinExpiryDays         // null = use default (90 days)
) {}