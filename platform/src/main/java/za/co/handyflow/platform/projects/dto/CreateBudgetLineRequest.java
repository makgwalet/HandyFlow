package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetLineRequest(

        @NotBlank(message = "Category is required")
        String category,            // LABOUR|MATERIALS|SUBCONTRACT|EQUIPMENT|OVERHEAD|CONTINGENCY

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        UUID phaseId,

        @NotNull(message = "Budgeted amount is required")
        @DecimalMin(value = "0.01", message = "Budgeted amount must be greater than zero")
        BigDecimal budgetedAmount,

        boolean isProvisional,

        boolean isPrimeCost

) {}
