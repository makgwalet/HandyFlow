package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** sku is intentionally not editable here: it is the item's identity. This is a full replace, so send every field you want to keep. */
public record UpdateItemRequest(
        @NotBlank String description, String uom, BigDecimal storageRatePerUnitPerMonth,
        String barcode, BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm, BigDecimal weightKg, BigDecimal unitCost,
        BigDecimal minQty, BigDecimal maxQty, String temperatureClass, Boolean hazardous, Boolean fragile, Boolean stackable,
        /** Three-letter code; blank means ZAR. */ String unitCostCurrency
) {}
