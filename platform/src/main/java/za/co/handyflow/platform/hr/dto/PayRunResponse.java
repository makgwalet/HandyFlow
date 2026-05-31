package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayRunResponse(
        UUID id,
        String payRunNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payDate,
        int taxYear,
        String status,
        BigDecimal totalGross,
        BigDecimal totalPaye,
        BigDecimal totalUif,
        BigDecimal totalSdl,
        BigDecimal totalNet,
        int employeeCount,
        String notes,
        Instant processedAt,
        Instant createdAt
) {}