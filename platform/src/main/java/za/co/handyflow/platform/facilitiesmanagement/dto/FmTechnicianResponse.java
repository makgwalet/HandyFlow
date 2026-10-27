package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmTechnicianResponse(
        UUID id, String name, String contactPhone, String contactEmail,
        String specialization, UUID linkedUserId, boolean active, Instant createdAt
) {}