package za.co.handyflow.platform.events.dto;

import java.util.UUID;

public record ContactResponse(
        UUID id,
        String contactType,
        String name,
        String organisation,
        String phone,
        String alternatePhone,
        String email,
        String notes
) {}