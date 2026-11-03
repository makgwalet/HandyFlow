package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * closureBlocker is filled only on the single-RFI endpoint (null elsewhere): the reason this RFI
 * cannot be closed right now, or null if it can. response is the LATEST answer — see /events for all of them.
 */
public record RfiResponse(
        UUID id, UUID projectId, String rfiNumber,
        String title, String description, String category,
        String requestedBy, UUID requestedById, LocalDate requestedDate, LocalDate dueDate,
        UUID assignedToId, String assignedToName, LocalDateTime assignedAt,
        String respondedBy, UUID respondedById, LocalDate respondedDate, String response,
        int clarificationCount,
        String status, boolean overdue, int daysUntilDue, int escalationLevel,
        UUID impactTaskId, boolean impactsSchedule, boolean impactsCost, String impactNotes, String noChangeNote,
        UUID changeOrderId, String closureBlocker,
        LocalDateTime closedAt, LocalDateTime cancelledAt, String cancellationReason,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {}