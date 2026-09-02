package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
        UUID id, String companyName, String serviceType, String contactName,
        String contactPhone, String contactEmail, String notes, boolean active, Instant createdAt
) {}
