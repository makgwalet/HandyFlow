package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateHealthEventRequest(
        UUID animalId,
        UUID groupId,
        @NotBlank String eventType,
        @NotNull LocalDate eventDate,
        @NotBlank String description,
        String productUsed,
        String dosage,
        UUID administeredBy,
        String veterinarian,
        BigDecimal cost,
        Integer withdrawalPeriodDays,
        LocalDate nextDueDate,
        String status,
        String notes
) {}
