package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePayClientRequest(
        @NotBlank String tradingName,
        String registrationNumber,
        String payeReference,
        String uifReference,
        String sdlReference,
        String payFrequency,
        Integer payDay,
        String contactName,
        String contactEmail,
        String address,
        String contactPhone
) {}