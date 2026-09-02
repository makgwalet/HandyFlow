package za.co.handyflow.platform.bookkeeping.dto;

import java.time.Instant;
import java.util.UUID;

public record BkProfileResponse(
        UUID id, String practiceName, String registrationNumber, String contactEmail,
        String contactPhone, String notes, Instant createdAt
) {}
