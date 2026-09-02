package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpMatterResponse(
        UUID id,
        UUID clientId,
        UUID attorneyId,
        String matterNumber,
        String matterType,
        String matterName,
        String description,
        String billingType,
        BigDecimal fixedFeeAmount,
        String status,
        LocalDate openedDate,
        LocalDate closedDate,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
