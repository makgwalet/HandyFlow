package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record GuardEnrollResponse(
        UUID guardId,
        String  fullName,
        boolean faceEmbeddingStored,
        boolean deviceRegistered,
        Instant pinExpiresAt,
        String  temporaryPin        // shown once, only when the server generated it; the guard must change it at first login
) {
    public GuardEnrollResponse(UUID guardId, String fullName, boolean faceEmbeddingStored,
                               boolean deviceRegistered, Instant pinExpiresAt) {
        this(guardId, fullName, faceEmbeddingStored, deviceRegistered, pinExpiresAt, null);
    }
}
