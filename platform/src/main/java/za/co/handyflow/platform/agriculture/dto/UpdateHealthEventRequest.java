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
        String notes,
        Integer milkWithdrawalDays,
        Integer eggWithdrawalDays
) {
    /** Without milk and egg periods (callers written before they existed). */
    public UpdateHealthEventRequest(String description, String productUsed, String dosage, String veterinarian, BigDecimal cost,
                                    Integer withdrawalPeriodDays, LocalDate nextDueDate, String notes) {
        this(description, productUsed, dosage, veterinarian, cost, withdrawalPeriodDays, nextDueDate, notes, null, null);
    }
}
