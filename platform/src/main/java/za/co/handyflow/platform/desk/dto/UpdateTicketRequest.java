package za.co.handyflow.platform.desk.dto;

import java.util.UUID;

public record UpdateTicketRequest(
        String subject,
        String description,
        UUID   categoryId,
        String priority,
        String notes
) {}
