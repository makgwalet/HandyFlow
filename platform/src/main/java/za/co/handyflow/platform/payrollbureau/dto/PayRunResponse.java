package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayRunResponse(
        UUID id, String payRunNumber, LocalDate periodStart, LocalDate periodEnd,
        LocalDate payDate, int taxYear, String status,
        BigDecimal totalGross, BigDecimal totalPaye, BigDecimal totalUif,
        BigDecimal totalSdl, BigDecimal totalNet, Integer employeeCount, Instant processedAt,
        UUID payGroupId,
        UUID calculatedBy, UUID approvedBy, Instant approvedAt, Instant finalisedAt, Instant reversedAt, String reversalReason,
        Instant sentToClientAt, Instant clientApprovedAt, Instant clientDeclinedAt, String clientDeclineReason
) {}