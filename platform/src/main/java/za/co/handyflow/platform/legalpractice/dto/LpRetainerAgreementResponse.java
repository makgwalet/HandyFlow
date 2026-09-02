package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpRetainerAgreementResponse(
        UUID id,
        UUID clientId,
        BigDecimal monthlyFee,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
