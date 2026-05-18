package za.co.handyflow.platform.identity.dto.response;

import java.util.Set;
import java.util.UUID;

public record  AuthResponse(
        String accessToken,
        String tokenType,      // Always "Bearer"
        long expiresIn,        // Seconds until expiry
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        Set<String> permissions
) {}
