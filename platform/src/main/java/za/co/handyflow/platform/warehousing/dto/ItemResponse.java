package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemResponse(
        UUID id, UUID clientId, String sku, String description, String uom, BigDecimal storageRatePerUnitPerMonth,
        boolean active,
        String barcode, BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm, BigDecimal volumeM3, BigDecimal weightKg,
        BigDecimal unitCost, BigDecimal minQty, BigDecimal maxQty, String temperatureClass, boolean hazardous, boolean fragile,
        boolean stackable, boolean lotTracked, boolean expiryTracked, Integer minShelfLifeDays
) {}
