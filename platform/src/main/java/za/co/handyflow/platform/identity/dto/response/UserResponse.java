package za.co.handyflow.platform.identity.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID        id,
        String      email,
        String      firstName,
        String      lastName,
        String      phone,
        String      jobTitle,
        String      department,
        String      status,          // ACTIVE | INACTIVE | LOCKED
        Set<String> roles,           // role names e.g. ["ADMIN"]
        Set<String> permissions,     // flattened permission names
        Instant     createdAt
) {}
