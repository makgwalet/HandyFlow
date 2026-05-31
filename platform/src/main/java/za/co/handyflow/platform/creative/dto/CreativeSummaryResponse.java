package za.co.handyflow.platform.creative.dto;

public record CreativeSummaryResponse(
        long briefingCount,
        long inProgressCount,
        long awaitingApprovalCount,
        long inRevisionCount,
        long approvedCount,
        long deliveredCount,
        long overdueCount
) {}
