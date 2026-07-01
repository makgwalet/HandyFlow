package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record PayrollPeriodResponse(
        UUID       id,
        UUID       branchId,
        String     name,
        String     periodType,
        LocalDate  periodStart,
        LocalDate  periodEnd,
        String     status,
        BigDecimal totalHours,
        Long       totalAmountCents,
        Double     totalAmountZar,
        int        lineItemCount,
        UUID       approvedBy,
        Instant    approvedAt,
        Instant    exportedAt,
        String     exportFormat,
        Instant    createdAt
) {}
