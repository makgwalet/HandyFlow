package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientComplianceDocumentResponse(
        UUID id, UUID clientId, UUID registrationId, String documentType, UUID evidenceId,
        LocalDate issueDate, LocalDate expiryDate, boolean verified, Instant verifiedAt, Instant createdAt
) {}
