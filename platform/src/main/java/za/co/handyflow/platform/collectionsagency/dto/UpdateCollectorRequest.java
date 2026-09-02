package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateCollectorRequest(
        @NotBlank String fullName, String registrationNumber, LocalDate registrationExpiryDate, String email,
        String phone
) {}
