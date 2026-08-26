package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FIX: backlog 6.3 — RfiController previously returned the raw
 * ProjectRfi JPA entity directly, the only module in this codebase that
 * did — every other module maps to a dedicated Response DTO. Also
 * carries the two new computed fields (overdue, daysUntilDue) and the
 * new changeOrderId link.
 */
public record RfiResponse(
        UUID id, UUID projectId, String rfiNumber,
        String title, String description, String category,
        String requestedBy, UUID requestedById, LocalDate requestedDate, LocalDate dueDate,
        String respondedBy, UUID respondedById, LocalDate respondedDate, String response,
        String status, boolean overdue, int daysUntilDue,
        UUID changeOrderId,
        LocalDateTime closedAt, LocalDateTime cancelledAt, String cancellationReason,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {}