package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateHealthEventRequest(
        String description,
        String productUsed,
        String dosage,
        String veterinarian,
        BigDecimal cost,
        Integer withdrawalPeriodDays,
        LocalDate nextDueDate,
        String notes
) {}
