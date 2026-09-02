package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmProfileResponse(
        UUID id, String companyName, String registrationNumber, String contactEmail,
        String contactPhone, String notes, Instant createdAt
) {}
