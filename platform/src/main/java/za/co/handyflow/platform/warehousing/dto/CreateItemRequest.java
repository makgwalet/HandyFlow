package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank String sku, @NotBlank String description, String uom, BigDecimal storageRatePerUnitPerMonth
) {}
