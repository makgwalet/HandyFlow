package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateScheduleRequest(
        @NotNull UUID patternId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {}