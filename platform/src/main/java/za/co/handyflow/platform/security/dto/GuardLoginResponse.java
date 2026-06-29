package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record GuardLoginResponse(
        String  accessToken,
        String  tokenType,      // "Bearer"
        long    expiresIn,      // seconds
        UUID    guardId,
        UUID tenantId,
        String  fullName,
        String  grade,
        String  status,
        boolean mustChangePIN,  // if true, redirect to PIN change before allowing other actions
        Instant expiresAt
) {}
