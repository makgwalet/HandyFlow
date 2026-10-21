package za.co.handyflow.platform.crm.dto;

import java.util.UUID;

public record ContactResponse(
        UUID id,
        UUID customerId,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String jobTitle,
        boolean primary,
        String notes
) {}
