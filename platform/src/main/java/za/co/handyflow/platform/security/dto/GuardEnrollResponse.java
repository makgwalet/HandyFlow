package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record GuardEnrollResponse(
        UUID guardId,
        String  fullName,
        boolean faceEmbeddingStored,
        boolean deviceRegistered,
        Instant pinExpiresAt
) {}
