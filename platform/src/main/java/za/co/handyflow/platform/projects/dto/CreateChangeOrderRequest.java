package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateChangeOrderRequest(

        @NotBlank(message = "Change order title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2 000 characters")
        String description,

        @Size(max = 1000, message = "Reason must not exceed 1 000 characters")
        String reason,

        // costImpact is optional (zero-cost scope changes are valid).
        // Signed: positive = cost increase, negative = scope reduction.
        @DecimalMin(value = "-99999999.99", message = "Cost impact is out of range")
        @DecimalMax(value =  "99999999.99", message = "Cost impact is out of range")
        BigDecimal costImpact,

        // Signed: positive = delay, negative = schedule recovery.
        @Min(value = -999, message = "Schedule impact in days is out of range")
        @Max(value =  999, message = "Schedule impact in days is out of range")
        int scheduleImpact

) {}
