package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.UUID;

public record TechnicianResponse(
        UUID id, String name, String contactPhone, String contactEmail,
        String specialization, UUID linkedUserId, boolean active, Instant createdAt
) {}
