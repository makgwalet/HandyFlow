package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID serviceId,
        UUID staffId,
        UUID customerId,
        @NotBlank String clientName,
        String clientEmail,
        String clientPhone,
        @NotNull LocalDate bookingDate,
        @NotNull LocalTime startTime,
        String notes
) {}