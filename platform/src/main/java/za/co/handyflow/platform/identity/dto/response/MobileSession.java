package za.co.handyflow.platform.identity.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * What a native app keeps. The web flow hands the refresh token over in an httpOnly cookie a phone cannot use,
 * so here it is in the body. The app must keep it in the keychain / keystore only.
 */
public record MobileSession(
        String accessToken, String tokenType, long expiresIn,
        UUID userId, UUID tenantId, String email, String firstName, String lastName,
        Set<String> permissions, String subscriptionStatus,
        String refreshToken, Instant refreshTokenExpiresAt
) {}
