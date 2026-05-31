package za.co.handyflow.platform.tasks.dto;
public record TasksSummaryResponse(
        long totalTasks,
        long todoCount,
        long inProgressCount,
        long inReviewCount,
        long doneCount,
        long overdueCount,
        long myTasksCount
) {}