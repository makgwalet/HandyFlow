package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portfolio report over the projects the caller can see.
 *
 * Delivery: projects completed in the period (completedAt in [from, to]); on time = completed on or before the baseline end
 * (or the end date when there is no baseline). Money only for projects whose cost the caller may see:
 * revenue = contract value, cost = actual cost; margin = revenue − cost for completed and closed projects, and
 * forecast margin = revenue − forecast at completion for live ones.
 */
public record ReportResponse(
        LocalDate from, LocalDate to, LocalDate generatedOn,
        Delivery delivery, Money money,
        List<ProjectRow> projects, List<GroupRow> byClient, List<GroupRow> byType,
        int hiddenCostProjects
) {
    public record Delivery(int completed, int onTime, Integer onTimePct, Integer avgFinishVarianceDays, int liveOverdue, int liveForecastLate) {}
    public record Money(BigDecimal revenue, BigDecimal cost, BigDecimal margin, Integer marginPct,
                        BigDecimal liveRevenue, BigDecimal liveForecastCost, BigDecimal liveForecastMargin, Integer liveForecastMarginPct,
                        BigDecimal costOverrun, int overBudgetProjects) {}
    public record ProjectRow(UUID id, String projectNumber, String name, String clientName, String projectType, String status, String health,
                             LocalDate startDate, LocalDate endDate, LocalDate baselineEnd, LocalDate completedOn, Boolean onTime, Integer finishVarianceDays,
                             boolean costVisible, BigDecimal contractValue, BigDecimal budget, BigDecimal actual, BigDecimal forecast,
                             BigDecimal margin, Integer marginPct, boolean forecastMargin) {}
    public record GroupRow(String key, int projects, int completed, int onTime, BigDecimal revenue, BigDecimal cost, BigDecimal margin, Integer marginPct) {}
}
