package za.co.handyflow.platform.trainingprovider.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        UUID enrollmentId,
        UUID delegateId,
        UUID clientId,
        String delegateNameSnapshot,
        String clientNameSnapshot,
        String courseTitleSnapshot,
        String unitStandardSnapshot,
        String certificateNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String status,
        String revokedReason,
        Instant createdAt
) {}
