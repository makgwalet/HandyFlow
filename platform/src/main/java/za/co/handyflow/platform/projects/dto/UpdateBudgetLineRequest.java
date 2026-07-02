package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Typed DTO for PATCH-style budget line updates.
 *
 * WHY THIS REPLACES Map&lt;String, Object&gt;:
 * ──────────────────────────────────────
 * The original ResourceController.updateBudgetLine() accepted:
 *   @RequestBody Map<String, Object> body
 *
 * Problems with that approach:
 *   1. No type safety — body.get("budgetedAmount") returns Object, requiring
 *      manual casting to BigDecimal with no compile-time check.
 *   2. No Bean Validation — invalid values (e.g. negative amounts) go straight
 *      through to the service without any rejection.
 *   3. Undiscoverable API — Swagger/OpenAPI shows "object" with no fields.
 *      Client developers have to guess the field names.
 *
 * With this record:
 *   1. BigDecimal is the declared type — Jackson deserialises it correctly.
 *   2. @DecimalMin rejects negative amounts at the controller layer.
 *   3. OpenAPI generates a proper schema with field names and constraints.
 *
 * Both fields are nullable — this is an intentional partial-update (PATCH)
 * pattern.  Null means "don't change this field."
 */
public record UpdateBudgetLineRequest(

        /**
         * New budgeted amount for this line — must be ≥ 0 if provided.
         * Null means "leave the existing amount unchanged."
         */
        @DecimalMin(value = "0.00", message = "Budgeted amount cannot be negative")
        BigDecimal budgetedAmount,

        /**
         * New description — max 300 chars (matches DB column).
         * Null means "leave the existing description unchanged."
         */
        @Size(max = 300, message = "Description must be 300 characters or fewer")
        String description
) {
}
