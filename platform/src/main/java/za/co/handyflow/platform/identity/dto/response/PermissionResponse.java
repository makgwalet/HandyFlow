package za.co.handyflow.platform.identity.dto.response;

import java.util.UUID;

public record PermissionResponse(
        UUID   id,
        String name,
        String description
) {}
