package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertFmVendorRequest(
        @NotBlank String companyName, String serviceType, String contactName,
        String contactPhone, String contactEmail, String notes
) {}
