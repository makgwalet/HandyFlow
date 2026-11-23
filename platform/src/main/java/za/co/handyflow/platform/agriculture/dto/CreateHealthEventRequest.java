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
        String notes,
        /** Days milk must stay out of sale after this treatment; null when not given. */
        Integer milkWithdrawalDays,
        /** Days eggs must stay out of sale after this treatment; null when not given. */
        Integer eggWithdrawalDays
) {
    /** Without milk and egg periods (callers written before they existed). */
    public CreateHealthEventRequest(UUID animalId, UUID groupId, String eventType, LocalDate eventDate, String description,
                                    String productUsed, String dosage, UUID administeredBy, String veterinarian, BigDecimal cost,
                                    Integer withdrawalPeriodDays, LocalDate nextDueDate, String status, String notes) {
        this(animalId, groupId, eventType, eventDate, description, productUsed, dosage, administeredBy, veterinarian, cost,
                withdrawalPeriodDays, nextDueDate, status, notes, null, null);
    }
}
