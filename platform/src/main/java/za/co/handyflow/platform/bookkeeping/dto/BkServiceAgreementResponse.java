package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BkServiceAgreementResponse(
        UUID id, UUID clientId, String billingType, BigDecimal monthlyFee, BigDecimal hourlyRate,
        LocalDate startDate, LocalDate endDate, String status, Instant createdAt
) {}
