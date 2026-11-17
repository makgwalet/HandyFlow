package za.co.handyflow.platform.supplychain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierDocumentResponse(
        UUID id,
        String docType,
        String referenceNumber,
        LocalDate issuedDate,
        LocalDate expiryDate,
        Long daysToExpiry,
        String status,
        boolean verified,
        String verifiedByName,
        Instant verifiedAt,
        boolean hasFile,
        String fileName,
        String uploadedByName,
        Instant createdAt
) {}
