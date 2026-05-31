package za.co.handyflow.platform.expenses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseClaimRequest(
        UUID employeeId,
        @NotBlank String employeeName,
        @NotNull  LocalDate claimDate,
        @NotBlank String category,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        String receiptUrl,
        String notes
) {}