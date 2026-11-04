package za.co.handyflow.platform.projects.dto;

import java.util.List;
import java.util.UUID;

/** Open risks on a 5 × 5 grid. cells[impact-1][probability-1] = number of open risks there. */
public record RiskHeatmapResponse(int[][] cells, int openRisks, List<TopRisk> top) {
    public record TopRisk(UUID id, UUID projectId, String projectNumber, String projectName, String riskNumber, String title,
                          int probability, int impact, String rating, String ownerName) {}
}
