package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateBookAgencyClientRequest(
        @NotBlank String tradingName,
        String businessType,
        String timezone,
        String contactName,
        String contactEmail,
        String contactPhone,
        BigDecimal monthlyRetainerAmount
) {}