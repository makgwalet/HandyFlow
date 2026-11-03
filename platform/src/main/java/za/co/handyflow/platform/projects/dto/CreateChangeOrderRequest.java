package za.co.handyflow.platform.projects.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Used for both create and update (update replaces the whole content of a DRAFT).
 *
 * costImpact is signed (positive = cost increase, negative = reduction). When costLines are given
 * and costImpact is omitted, it defaults to their total; when both are given they MUST agree.
 * A negative impact needs costLines saying which budget lines it reduces.
 * scheduleImpact is in calendar days (positive = delay) and moves the project end date and baseline end.
 * affectedTasks name the tasks that slip (in WORKING days); their dependents are pushed out on approval.
 */
public record CreateChangeOrderRequest(

        @NotBlank(message = "Change order title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2 000 characters")
        String description,

        @Size(max = 1000, message = "Reason must not exceed 1 000 characters")
        String reason,

        @DecimalMin(value = "-99999999.99", message = "Cost impact is out of range")
        @DecimalMax(value =  "99999999.99", message = "Cost impact is out of range")
        BigDecimal costImpact,

        @Min(value = -999, message = "Schedule impact in days is out of range")
        @Max(value =  999, message = "Schedule impact in days is out of range")
        int scheduleImpact,

        @Size(max = 2000, message = "Scope impact must not exceed 2 000 characters")
        String scopeImpact,

        @Size(max = 2000, message = "Risk impact must not exceed 2 000 characters")
        String riskImpact,

        @Size(max = 2000, message = "Procurement impact must not exceed 2 000 characters")
        String procurementImpact,

        @Valid @Size(max = 50, message = "At most 50 cost lines")
        List<CostLineRequest> costLines,

        @Valid @Size(max = 100, message = "At most 100 affected tasks")
        List<AffectedTaskRequest> affectedTasks
) {
    /** budgetLineId null = create a new budget line on approval (category required, amount positive). */
    public record CostLineRequest(
            UUID budgetLineId,
            @Size(max = 50, message = "Category must not exceed 50 characters") String category,
            @Size(max = 300, message = "Description must not exceed 300 characters") String description,
            @NotNull(message = "Amount is required") BigDecimal amount) {}

    public record AffectedTaskRequest(
            @NotNull(message = "Task id is required") UUID taskId,
            @Min(value = 0, message = "Delay cannot be negative")
            @Max(value = 365, message = "Delay cannot exceed 365 working days") int delayDays) {}
}