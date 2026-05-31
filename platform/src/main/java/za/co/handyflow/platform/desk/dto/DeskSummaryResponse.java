package za.co.handyflow.platform.desk.dto;

public record DeskSummaryResponse(
        long openCount,
        long inProgressCount,
        long waitingCount,
        long resolvedCount,
        long urgentOpen,
        long slaBreachedCount,
        long helpdeskCount,
        long internalCount
) {}
