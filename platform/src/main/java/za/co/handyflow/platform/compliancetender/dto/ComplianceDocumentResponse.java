package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ComplianceDocumentResponse(
        UUID id, UUID registrationId, String documentType, UUID evidenceId,
        LocalDate issueDate, LocalDate expiryDate, boolean verified, Instant verifiedAt, Instant createdAt
) {}
