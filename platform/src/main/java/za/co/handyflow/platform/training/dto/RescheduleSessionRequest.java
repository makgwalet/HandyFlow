package za.co.handyflow.platform.training.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RescheduleSessionRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
