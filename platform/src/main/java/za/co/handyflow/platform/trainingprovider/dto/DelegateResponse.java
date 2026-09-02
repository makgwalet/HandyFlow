package za.co.handyflow.platform.trainingprovider.dto;

import java.time.Instant;
import java.util.UUID;

public record DelegateResponse(
        UUID id,
        UUID clientId,
        String delegateNumber,
        String fullName,
        String idNumber,
        String email,
        String phone,
        String jobTitle,
        String status,
        Instant createdAt
) {}
