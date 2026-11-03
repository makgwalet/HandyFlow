package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What approving this change order WOULD do — nothing is changed by asking.
 *
 * cost.problems are things that would make approval fail (e.g. a reduction below what is spent).
 * schedule compares the critical-path forecast finish with and without the affected tasks' delays.
 * computedFinishDeltaDays is in calendar days; declaredScheduleImpactDays is what the change order
 * claims for the project end date — a gap between them is reported in warnings.
 */
public record ImpactAnalysisResponse(
        UUID changeOrderId, String changeNumber, Cost cost, Schedule schedule, List<String> warnings
) {
    public record Cost(BigDecimal declaredCostImpact, BigDecimal revisedBudgetBefore, BigDecimal revisedBudgetAfter,
                       BigDecimal contingencyRemaining, boolean exceedsContingency, List<String> problems) {}

    public record Schedule(int declaredScheduleImpactDays, LocalDate forecastFinishBefore,
                           LocalDate forecastFinishAfter, Integer computedFinishDeltaDays,
                           int affectedTaskCount, List<UUID> newlyCriticalTaskIds, boolean cycleDetected) {}
}