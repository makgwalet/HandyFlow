package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        String contractNumber,
        String title,
        String contractType,
        String status,
        BigDecimal valueAmount,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        boolean autoRenew,
        String notes,
        Instant sentAt,
        Instant signedAt,
        Instant terminatedAt,
        String terminationReason,
        List<PartyResponse> parties,
        Instant createdAt
) {}