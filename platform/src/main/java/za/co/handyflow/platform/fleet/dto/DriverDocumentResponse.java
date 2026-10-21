package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverDocumentResponse(
        UUID id,
        UUID driverId,
        String type,
        String title,
        String documentNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String issuer,
        String notes,
        int version,
        String status,          // CURRENT | SUPERSEDED
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant uploadedAt,
        boolean expired,        // computed from expiryDate
        Long daysToExpiry       // null when no expiry; negative once expired
) {}
