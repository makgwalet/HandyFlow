package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cost centres, departments, branches and projects (finance F24). */
public final class CostCentreDtos {
    private CostCentreDtos() {}

    public record Save(String code, String name, String kind, UUID parentId) {}
    public record SetActive(boolean active) {}
    public record CostCentre(UUID id, String code, String name, String kind, UUID parentId, boolean active, long lineCount) {}

    /** One column of the by-cost-centre report. id is null for the "Unallocated" column. */
    public record Column(UUID id, String code, String name, String kind) {}
    /** amounts line up with the report's columns; total counts each line once, so it never doubles when rolling up. */
    public record Row(UUID accountId, String code, String name, String type, List<BigDecimal> amounts, BigDecimal total) {}
    public record ByCentreReport(LocalDate from, LocalDate to, boolean rollUp, List<Column> columns,
                                 List<Row> income, List<Row> expenses,
                                 List<BigDecimal> incomeTotals, List<BigDecimal> expenseTotals, List<BigDecimal> resultTotals,
                                 BigDecimal incomeTotal, BigDecimal expenseTotal, BigDecimal resultTotal) {}

    public record CentreLine(LocalDate date, String entryNumber, String entryDescription, String accountCode, String accountName,
                             String lineDescription, BigDecimal amount) {}
    public record CentreDetail(CostCentre centre, LocalDate from, LocalDate to, BigDecimal income, BigDecimal expenses, BigDecimal result,
                               List<CentreLine> lines, boolean truncated) {}
}
