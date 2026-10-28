package za.co.handyflow.platform.training.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID courseId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String venue,
        String trainerName,
        Integer capacity,
        String notes,
        Boolean openBooking
) {
    public CreateSessionRequest(UUID courseId, LocalDate startDate, LocalDate endDate, String venue, String trainerName, Integer capacity, String notes) {
        this(courseId, startDate, endDate, venue, trainerName, capacity, notes, null);
    }
}
