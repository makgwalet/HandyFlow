package za.co.handyflow.platform.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID    id,
        String  email,
        String  firstName,
        String  lastName,
        String  jobTitle,
        String  department,
        String  roleName,
        String  status,       // PENDING | ACCEPTED | EXPIRED | CANCELLED
        Instant expiresAt,
        Instant createdAt
) {}
