package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlacementResponse(
        UUID id, UUID requisitionId, String requisitionTitle,
        UUID candidateId, String candidateName, UUID clientId,
        String stage, BigDecimal offeredSalary, BigDecimal placementFeeAmount,
        Instant placedAt, LocalDate guaranteeEndsAt, String notes, Instant createdAt
) {}