package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCollectorRequest(
        UUID userId, @NotBlank String fullName, String registrationNumber, LocalDate registrationExpiryDate,
        String email, String phone
) {}
