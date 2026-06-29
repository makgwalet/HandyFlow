package za.co.handyflow.platform.security.dto;


import java.util.UUID;

public record RevokeTokensResponse(
        UUID guardId,
        int     tokensRevoked,
        String  reason
) {}
