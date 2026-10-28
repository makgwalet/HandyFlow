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
        Instant createdAt,
        String paymentRef,
        LocalDate paidOn,
        BigDecimal paidAmount,
        boolean overdue,
        boolean late,
        boolean runIssued,
        BigDecimal totalEti,
        BigDecimal etiCarried
) {
    /** Without the employment tax incentive. */
    public Emp201Response(UUID id, UUID payRunId, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal totalPaye,
                          BigDecimal totalUif, BigDecimal totalSdl, BigDecimal totalPayable, String status, Instant submittedAt,
                          Instant createdAt, String paymentRef, LocalDate paidOn, BigDecimal paidAmount, boolean overdue, boolean late,
                          boolean runIssued) {
        this(id, payRunId, periodStart, periodEnd, dueDate, totalPaye, totalUif, totalSdl, totalPayable, status, submittedAt, createdAt,
                paymentRef, paidOn, paidAmount, overdue, late, runIssued, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}