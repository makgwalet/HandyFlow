package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Budgets and the cash forecast (finance F21). */
public final class BudgetDtos {
    private BudgetDtos() {}

    public record CreateBudget(String name, LocalDate startDate) {}
    public record LineIn(UUID accountId, int month, BigDecimal amount) {}
    public record SetLines(List<LineIn> lines) {}
    public record CopyActuals(BigDecimal upliftPercent) {}

    public record BudgetSummary(UUID id, String name, LocalDate startDate, LocalDate endDate, String status,
                                BigDecimal income, BigDecimal expense, int lines) {}
    public record AccountLine(UUID accountId, String code, String name, String type, List<BigDecimal> months, BigDecimal total) {}
    public record BudgetDetail(BudgetSummary summary, List<AccountLine> accounts) {}

    public record VarianceRow(UUID accountId, String code, String name, String type, BigDecimal budget, BigDecimal actual,
                              BigDecimal variance, BigDecimal percent, boolean favourable) {}
    public record VarianceReport(UUID budgetId, String name, LocalDate from, LocalDate through, int monthsCounted,
                                 List<VarianceRow> rows, BigDecimal budgetIncome, BigDecimal actualIncome,
                                 BigDecimal budgetExpense, BigDecimal actualExpense, BigDecimal budgetNet, BigDecimal actualNet) {}

    public record ItemRequest(LocalDate date, BigDecimal amount, String direction, String label) {}
    public record ItemResponse(UUID id, LocalDate date, BigDecimal amount, String direction, String label) {}

    public record ForecastItem(LocalDate date, BigDecimal amount, String direction, String label, String source) {}
    public record ForecastWeek(int index, LocalDate start, LocalDate end, BigDecimal opening, BigDecimal in, BigDecimal out, BigDecimal closing) {}
    public record ForecastResponse(LocalDate today, BigDecimal openingCash, int customerDelayDays, int overdueReceiptInDays,
                                   int overduePayInDays, int collectPercent, BigDecimal lowBalance,
                                   List<ForecastWeek> weeks, BigDecimal lowest, LocalDate lowestWeekStart, Integer firstNegativeWeek,
                                   BigDecimal laterIn, BigDecimal laterOut, List<String> warnings, List<ForecastItem> items,
                                   BigDecimal receivablesTotal, BigDecimal payablesTotal) {}
}
