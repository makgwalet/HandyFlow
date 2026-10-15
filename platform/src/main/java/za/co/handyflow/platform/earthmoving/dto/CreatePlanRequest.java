package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePlanRequest(
        @NotNull UUID assetId,
        @NotBlank String name,
        @NotBlank String taskType,
        @NotBlank String triggerType,     // HOURS | DAYS
        @NotNull Integer intervalValue,
        Integer leadValue,                // warn this far ahead (default 0)
        BigDecimal lastDoneHours,         // when it was last done; default: the machine's hours now
        LocalDate lastDoneDate,           // default: today
        String priority,
        Boolean blocksRelease
) {}
