package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmVendorResponse(
        UUID id, String companyName, String serviceType, String contactName,
        String contactPhone, String contactEmail, String notes, boolean active, Instant createdAt
) {}
