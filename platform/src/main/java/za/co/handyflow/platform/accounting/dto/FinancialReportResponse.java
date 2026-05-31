package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialReportResponse(
        String reportType,
        LocalDate fromDate,
        LocalDate toDate,
        List<ReportSection> sections,
        BigDecimal netResult
) {
    public record ReportSection(
            String title,
            List<ReportLine> lines,
            BigDecimal total
    ) {}

    public record ReportLine(
            String accountCode,
            String accountName,
            BigDecimal amount
    ) {}
}