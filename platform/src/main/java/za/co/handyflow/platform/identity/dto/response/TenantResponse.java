package za.co.handyflow.platform.identity.dto.response;

import java.util.UUID;

public record  TenantResponse(
        UUID id,
        String name,
        String slug,
        String email,
        String status
) {}
