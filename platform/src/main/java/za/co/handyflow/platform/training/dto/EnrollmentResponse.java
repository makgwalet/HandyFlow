package za.co.handyflow.platform.training.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID sessionId,
        UUID employeeId,
        String employeeNameSnapshot,
        String employeeNumberSnapshot,
        String status,
        Instant enrolledAt,
        Instant completedAt,
        BigDecimal score,
        Boolean passed,
        String notes,
        String cancelReason
) {}
