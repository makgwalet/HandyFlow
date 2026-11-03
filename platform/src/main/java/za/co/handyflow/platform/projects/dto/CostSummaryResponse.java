package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.application.internal.cost.CostEngine;

import java.time.Instant;
import java.util.UUID;

/** Cost position of one project. See CostEngine's Javadoc for exactly what each figure means. */
public record CostSummaryResponse(UUID projectId, Instant generatedAt, CostEngine.Summary summary) {}