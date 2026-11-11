package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerSiteRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 300) String address,
        @Size(max = 120) String contactName,
        @Size(max = 30) String contactPhone,
        @Size(max = 500) String deliveryInstructions,
        @Size(max = 100) String operatingHours,
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("5000") java.math.BigDecimal distanceKm
) {}
