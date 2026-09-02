package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertVendorRequest(
        @NotBlank String companyName, String serviceType, String contactName,
        String contactPhone, String contactEmail, String notes
) {}
