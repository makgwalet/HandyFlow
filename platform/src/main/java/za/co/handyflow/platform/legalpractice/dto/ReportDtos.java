package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() { }

    /** Matters by status and kind. {@code hidden} is true when ethical walls left some matters out, so the counts are not the whole firm. */
    public record MatterCount(String status, String matterType, long count) { }
    public record MattersReport(List<MatterCount> rows, long total, long open, boolean hidden) { }

    public record UnbilledRow(UUID matterId, String matterLabel, String clientName, BigDecimal hours, BigDecimal timeValue, BigDecimal disbursements, BigDecimal total) { }
    public record UnbilledReport(List<UnbilledRow> rows, BigDecimal hours, BigDecimal timeValue, BigDecimal disbursements, BigDecimal total, boolean truncated, boolean hidden) { }

    public record DebtorRow(UUID invoiceId, String invoiceNumber, String clientName, String matterLabel, LocalDate issueDate, LocalDate dueDate, BigDecimal total, BigDecimal paid,
                            BigDecimal outstanding, long daysOverdue, String bucket) { }
    public record Bucket(String key, String label, BigDecimal amount, long count) { }
    public record DebtorsReport(List<DebtorRow> rows, List<Bucket> buckets, BigDecimal outstanding, BigDecimal overdue, boolean truncated, boolean hidden) { }

    public record BillingRow(String month, String status, long invoices, BigDecimal subtotal, BigDecimal vat, BigDecimal total, BigDecimal paid) { }
    public record BillingMonth(String month, long invoices, BigDecimal invoiced, BigDecimal paid, BigDecimal writtenOff) { }
    public record BillingReport(LocalDate from, LocalDate to, List<BillingMonth> months, BigDecimal invoiced, BigDecimal vat, BigDecimal paid, BigDecimal writtenOff, BigDecimal collectedPercent, boolean hidden) { }

    public record TrustRow(UUID clientId, String clientName, BigDecimal balance, LocalDate lastMovement) { }
    public record TrustReport(LocalDate asAt, List<TrustRow> rows, BigDecimal total, long overdrawn, boolean hidden) { }
}
