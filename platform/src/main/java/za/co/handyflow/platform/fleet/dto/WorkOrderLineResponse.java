package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderLineResponse(
        UUID id,
        String lineType,
        String description,
        String partNumber,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        UUID technicianId,
        String technicianName
) {}