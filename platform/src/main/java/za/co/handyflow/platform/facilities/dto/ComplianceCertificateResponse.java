package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ComplianceCertificateResponse(
        UUID id, UUID siteId, UUID assetId, String certificateType, String certificateNumber,
        String issuedBy, LocalDate issueDate, LocalDate expiryDate, String documentRef,
        String status, String revokedReason, Instant createdAt
) {}
