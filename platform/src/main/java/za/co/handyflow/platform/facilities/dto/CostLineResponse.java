package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CostLineResponse(
        UUID id, UUID workOrderId, String costType, String description, BigDecimal quantity,
        BigDecimal unitCost, BigDecimal totalCost, UUID recordedByUserId, Instant recordedAt
) {}