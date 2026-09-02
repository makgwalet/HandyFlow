package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.UUID;

public record LpProfileResponse(
        UUID id,
        String firmName,
        String practiceNumber,
        String vatNumber,
        String contactEmail,
        String contactPhone,
        String trustBankName,
        String trustAccountNumber,
        String businessBankName,
        String businessAccountNumber,
        Instant createdAt,
        Instant updatedAt
) {}
