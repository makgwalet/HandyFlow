package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Emp201Response(
        UUID id,
        UUID payRunId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        BigDecimal totalPaye,
        BigDecimal totalUif,
        BigDecimal totalSdl,
        BigDecimal totalPayable,
        String status,
        Instant submittedAt,
        Instant createdAt
) {}