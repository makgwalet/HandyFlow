package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertFmTechnicianRequest(
        @NotBlank String name, String contactPhone, String contactEmail, String specialization
) {}
