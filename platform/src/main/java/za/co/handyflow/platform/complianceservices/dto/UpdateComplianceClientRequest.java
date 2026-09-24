package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateComplianceClientRequest(
        @NotBlank String name, String contactEmail, String contactPhone, String mandateNotes
) {}
