package za.co.handyflow.platform.projects.dto;

public record ProjectSummaryResponse(
        long   activeProjects,
        long   redProjects,
        long   amberProjects,
        long   pendingTimeApprovals,
        int    openRedRisks
) {}
