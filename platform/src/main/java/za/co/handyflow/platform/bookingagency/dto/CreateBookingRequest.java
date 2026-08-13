package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID resourceId,
        @NotNull UUID offeringId,
        @NotBlank String customerName,
        String customerPhone,
        String customerEmail,
        @NotNull LocalDateTime startDatetime,
        String notes
) {}