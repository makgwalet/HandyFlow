package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record PrincipalResponse(
        UUID    id,
        String  fullName,
        String  aliasCodename,
        String  threatLevel,
        String  medicalNotes,
        String  knownThreats,
        String  emergencyContactsJson,
        String  photoUrl,
        boolean active,
        Instant createdAt
) {}
