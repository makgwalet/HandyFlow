package za.co.handyflow.platform.debtcollection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Collection strategies (finance F23). */
public final class StrategyDtos {
    private StrategyDtos() {}

    public record StepIn(int afterDays, String action, String instruction) {}
    public record StrategyRequest(String name, String description, int priority, BigDecimal minBalance, BigDecimal maxBalance,
                                  Integer minDays, Integer maxDays, int quietDays, List<StepIn> steps) {}
    public record StepOut(UUID id, int afterDays, String action, String instruction) {}
    public record StrategyResponse(UUID id, String name, String description, int priority, BigDecimal minBalance, BigDecimal maxBalance,
                                   Integer minDays, Integer maxDays, int quietDays, boolean active, List<StepOut> steps) {}
    public record ActiveRequest(boolean active) {}

    /** One case as the engine sees it. {@code decision} is RAISE, WAIT, SKIP or NONE. */
    public record PreviewRow(UUID caseId, String caseNumber, String debtorName, BigDecimal balance, long daysOverdue, String strategyName,
                             String decision, String stepAction, Integer stepDay, String title, String reason) {}
    public record RunResult(int considered, int raised, int skippedLate, List<PreviewRow> rows) {}

    public record HistoryRow(LocalDate appliedOn, String strategyName, int stepDay, String action, String outcome, int daysOverdue) {}
    public record CaseStrategy(UUID caseId, String strategyName, long daysOverdue, BigDecimal balance, Integer nextStepDay, String nextAction,
                               String reason, List<HistoryRow> history) {}
}
