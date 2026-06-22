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
            BigDecimal amount,         // net amount (debit - credit)
            BigDecimal grossDebit,     // trial balance gross debit column (null for P&L / BS)
            BigDecimal grossCredit     // trial balance gross credit column (null for P&L / BS)
    ) {
        // Convenience constructor for P&L and Balance Sheet (no gross columns needed)
        public ReportLine(String accountCode, String accountName, BigDecimal amount) {
            this(accountCode, accountName, amount, null, null);
        }
    }
}
