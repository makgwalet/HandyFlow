package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID courseId,
        @NotBlank String sessionType,
        UUID clientId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String venue,
        String trainerName,
        Integer capacity,
        String notes
) {}
