package za.co.handyflow.platform.identity.dto.response;

import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID        id,
        String      name,
        String      description,
        Set<String> permissions,    // permission names assigned to this role
        int         userCount       // how many users have this role
) {}
