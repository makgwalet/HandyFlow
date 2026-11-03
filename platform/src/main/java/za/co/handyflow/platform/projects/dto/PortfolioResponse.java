package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portfolio figures for the Projects dashboard, over the projects the caller can see.
 *
 * Money only counts projects whose cost the caller may see ({@code costProjects} of {@code activeAndPlanned}).
 * {@code previous} is the closing snapshot of last month (null until a month of snapshots exists), so the screen can show
 * "vs end of last month". {@code trend} is up to six months of month-end figures, oldest first.
 *
 * Overdue = approved, active or on hold, and past its end date.
 */
public record PortfolioResponse(
        LocalDate asOf,
        Counts now,
        Counts previous,
        LocalDate previousMonth,
        int costProjects,
        int hiddenCostProjects,
        List<Milestone> upcomingMilestones,
        List<TrendPoint> trend
) {
    public record Counts(int total, int active, int onTrack, int atRisk, int overdue, int onHold, int green, int amber, int red,
                         BigDecimal budget, BigDecimal spent, BigDecimal committed) {}
    public record Milestone(UUID taskId, UUID projectId, String projectNumber, String projectName, String taskNumber, String title,
                            LocalDate due, int daysAway) {}
    public record TrendPoint(LocalDate month, int onTrack, int atRisk, int overdue, BigDecimal spent) {}
}
