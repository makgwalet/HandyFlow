package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank String sku, @NotBlank String description, String uom, BigDecimal storageRatePerUnitPerMonth,
        String barcode, BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm, BigDecimal weightKg, BigDecimal unitCost,
        BigDecimal minQty, BigDecimal maxQty, String temperatureClass, Boolean hazardous, Boolean fragile, Boolean stackable,
        /** Three-letter code; blank means ZAR. */ String unitCostCurrency
) {}
