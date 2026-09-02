package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RescheduleSessionRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
