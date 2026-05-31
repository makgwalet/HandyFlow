package za.co.handyflow.platform.desk.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateTicketRequest(
        String     channel,          // INTERNAL | HELPDESK (default HELPDESK)
        @NotBlank  String requesterName,
                   String requesterEmail,
                   String requesterPhone,
                   UUID   customerId,
        @NotBlank  String subject,
        @NotBlank  String description,
                   UUID   categoryId,
                   String priority,
                   UUID   assignedTo
) {}
