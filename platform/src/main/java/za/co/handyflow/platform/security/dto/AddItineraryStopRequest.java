package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;

public record AddItineraryStopRequest(
        @NotBlank String locationName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant scheduledArrival,
        Instant scheduledDeparture,
        boolean advanceSurveyRequired,
        String notes
) {}
