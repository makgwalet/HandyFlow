package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLineResponse(
        UUID id,
        String category,
        String description,
        BigDecimal plannedAmount,
        String notes
) {}