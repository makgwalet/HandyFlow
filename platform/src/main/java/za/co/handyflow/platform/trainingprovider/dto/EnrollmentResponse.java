package za.co.handyflow.platform.trainingprovider.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID sessionId,
        UUID delegateId,
        UUID clientId,
        String delegateNameSnapshot,
        String status,
        Instant enrolledAt,
        Instant completedAt,
        BigDecimal score,
        Boolean passed,
        String notes,
        String cancelReason,
        boolean invoiced
) {}
