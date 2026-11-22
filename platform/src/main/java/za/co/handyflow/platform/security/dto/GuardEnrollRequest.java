package za.co.handyflow.platform.security.dto;

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
        @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
        String pin,                   // optional: leave blank and the server generates one (recommended)

        String faceEmbeddingBase64,   // Base64 float vector from on-device face model; null = skip
        String deviceHardwareId,      // hardware ID of the guard's assigned device; null = skip

        Integer pinExpiryDays,        // null = use default (90 days)

        // The guard agreed to keep a face template. Without true here the template is NOT stored.
        Boolean faceConsent
) {
    /** For callers that send no face data (bulk app access): consent is not needed. */
    public GuardEnrollRequest(String pin, String faceEmbeddingBase64, String deviceHardwareId, Integer pinExpiryDays) {
        this(pin, faceEmbeddingBase64, deviceHardwareId, pinExpiryDays, null);
    }
}