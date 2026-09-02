package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateInsBrokInsurerRequest(
        @NotBlank String name,
        String contactName,
        String contactEmail,
        String contactPhone,
        String notes
) {}
