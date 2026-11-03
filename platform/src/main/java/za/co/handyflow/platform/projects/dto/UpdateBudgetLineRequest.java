package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * PATCH-style update; null means "don't change this field".
 *
 * CHUNK 4: reason is required whenever budgetedAmount changes on a project whose baseline is
 * locked. Such a change is recorded as a budget revision; it can never push the budget below what
 * is already spent + committed on the line (use a transfer or a forecast instead).
 */
public record UpdateBudgetLineRequest(

        @DecimalMin(value = "0.00", message = "Budgeted amount cannot be negative")
        BigDecimal budgetedAmount,

        @Size(max = 300, message = "Description must be 300 characters or fewer")
        String description,

        @Size(max = 2000, message = "Reason must not exceed 2 000 characters")
        String reason
) {
}