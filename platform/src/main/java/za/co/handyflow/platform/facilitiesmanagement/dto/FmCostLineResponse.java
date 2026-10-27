package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FmCostLineResponse(
        UUID id, UUID workOrderId, String costType, String description, BigDecimal quantity,
        BigDecimal unitCost, BigDecimal totalCost, BigDecimal unitCharge, BigDecimal totalCharge,
        boolean billable, UUID recordedByUserId, Instant recordedAt
) {}