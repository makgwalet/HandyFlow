package za.co.handyflow.platform.desk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicTicketResponse(
        UUID    id,
        String  ticketNumber,
        String  subject,
        String  status,
        String  priority,
        Instant createdAt,
        Instant updatedAt,
        List<DeskCommentResponse> comments  // only non-internal comments
) {}
