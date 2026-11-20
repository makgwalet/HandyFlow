package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** recessStart and recessEnd are "MM-DD" (for example "12-16" and "01-15"); give both or neither. */
public record DeadlineRuleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String court,
        @NotBlank String triggerLabel,
        @NotBlank String keyDateType,
        @NotNull Integer amount,
        @NotBlank String unit,
        @NotBlank String direction,
        @NotBlank String rollover,
        String recessStart,
        String recessEnd,
        String sourceReference
) {}
