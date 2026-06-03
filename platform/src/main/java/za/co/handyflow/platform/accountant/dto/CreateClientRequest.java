package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

// Client management
public record CreateClientRequest(
        @NotBlank String entityType,
        @NotBlank String tradingName,
        String registeredName,
        String registrationNumber,
        String taxReferenceNumber,
        String vatNumber,
        String vatCategory,
        @Min(1) @Max(12) int yearEndMonth,
        String contactEmail,
        String contactPhone,
        UUID crmCustomerId
) {
}
