package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(
        UUID id,
        UUID catalogueItemId,
        String description,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal lineTotal,
        BigDecimal vatAmount
) {}
