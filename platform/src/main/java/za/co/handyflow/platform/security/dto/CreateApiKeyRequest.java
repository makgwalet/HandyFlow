package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record CreateApiKeyRequest(
        @NotBlank String name,
        String scopePrefixesJson,  // JSON array: ["/api/v1/security/reports"]
        UUID   branchId,
        boolean readOnly,
        Instant expiresAt
) {}
