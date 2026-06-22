package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID       id,
        UUID       patientId,
        String     patientName,
        String     method,
        BigDecimal amount,
        String     reference,
        Instant    recordedAt,
        String     notes
) {}
