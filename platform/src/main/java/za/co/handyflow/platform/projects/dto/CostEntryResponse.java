package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectCostEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param duplicate       true when an identical earlier posting with the same idempotency key was returned
 * @param lineOverBudget  true when the line's actual + committed now exceeds its budget (posting is still accepted — costs are facts)
 */
public record CostEntryResponse(
        UUID id, UUID budgetLineId, String type, BigDecimal amount,
        String sourceType, String sourceRef, String description, LocalDate entryDate,
        UUID reversalOf, String idempotencyKey, String postedByName, Instant createdAt,
        boolean duplicate, boolean lineOverBudget
) {
    public static CostEntryResponse of(ProjectCostEntry e, boolean duplicate, boolean lineOverBudget) {
        return new CostEntryResponse(
                e.getId(), e.getBudgetLineId(), e.getEntryType().name(), e.getAmount(),
                e.getSourceType(), e.getSourceRef(), e.getDescription(), e.getEntryDate(),
                e.getReversalOf(), e.getIdempotencyKey(), e.getPostedByName(), e.getCreatedAt(),
                duplicate, lineOverBudget);
    }
}