package za.co.handyflow.platform.training.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        UUID enrollmentId,
        UUID employeeId,
        String employeeNameSnapshot,
        String courseTitleSnapshot,
        String certificateNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String status,
        String revokedReason,
        Instant createdAt
) {}
