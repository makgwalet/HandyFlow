package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String vendorType,
        String companyName,
        String contactName,
        String contactPhone,
        String contactEmail,
        String serviceDescription,
        BigDecimal quotedAmount,
        boolean confirmed,
        String notes,
        Instant createdAt
) {}