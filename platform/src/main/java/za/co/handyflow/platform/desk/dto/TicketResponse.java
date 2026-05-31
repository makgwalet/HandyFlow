package za.co.handyflow.platform.desk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID    id,
        String  ticketNumber,
        String  channel,
        String  requesterName,
        String  requesterEmail,
        String  requesterPhone,
        UUID    customerId,
        String  subject,
        String  description,
        UUID    categoryId,
        String  categoryName,
        String  priority,
        String  status,
        UUID    assignedTo,
        String  assignedToName,
        boolean slaBreached,
        Instant dueAt,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant closedAt,
        String  publicToken,
        String  notes,
        List<DeskCommentResponse> comments,
        Instant createdAt,
        Instant updatedAt
) {}
