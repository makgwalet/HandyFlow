package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateClientRequest(
        String tradingName,
        String registeredName,
        String registrationNumber,
        String taxReferenceNumber,
        String vatNumber,
        String vatCategory,
        @Min(1) @Max(12) Integer yearEndMonth,
        String contactEmail,
        String contactPhone,
        String riskRating
) {
}
