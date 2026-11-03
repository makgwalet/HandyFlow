package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetTransferRequest(
        @NotNull(message = "Source budget line is required") UUID fromLineId,
        @NotNull(message = "Destination budget line is required") UUID toLineId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be greater than zero") BigDecimal amount,
        @NotBlank(message = "A reason is required for a budget transfer")
        @Size(max = 2000, message = "Reason must not exceed 2 000 characters") String reason
) {}