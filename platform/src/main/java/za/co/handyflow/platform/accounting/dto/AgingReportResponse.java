package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AgingReportResponse(
        LocalDate asAt,
        List<AgingLine> lines,
        BigDecimal current,    // not yet due
        BigDecimal days1to30,
        BigDecimal days31to60,
        BigDecimal days61to90,
        BigDecimal over90,
        BigDecimal total
) {
    public record AgingLine(
            UUID invoiceId,
            String invoiceNumber,
            String customerName,   // CRM name or walk-in name
            LocalDate dueDate,
            int daysOverdue,
            BigDecimal balance,
            String bucket          // CURRENT | 1-30 | 31-60 | 61-90 | 90+
    ) {}
}
