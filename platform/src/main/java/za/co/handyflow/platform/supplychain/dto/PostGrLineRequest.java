package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PostGrLineRequest(
        UUID catalogueItemId,
        String itemName,
        BigDecimal qtyReceived,
        BigDecimal unitCost,
        String lotNumber,
        LocalDate expiryDate
) {}
