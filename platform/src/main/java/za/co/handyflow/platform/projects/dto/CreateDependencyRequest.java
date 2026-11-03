package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * type: FS (finish-to-start, default) | SS | FF | SF.
 * lagDays: working days (0–365). Negative lag (lead time) is not supported.
 */
public record CreateDependencyRequest(
        @NotNull(message = "Predecessor task id is required") UUID predecessorId,
        @NotNull(message = "Successor task id is required")   UUID successorId,

        @Pattern(regexp = "(?i)FS|SS|FF|SF", message = "type must be one of FS, SS, FF, SF")
        String type,

        @Min(value = 0,   message = "lagDays cannot be negative")
        @Max(value = 365, message = "lagDays cannot exceed 365")
        Integer lagDays
) {}