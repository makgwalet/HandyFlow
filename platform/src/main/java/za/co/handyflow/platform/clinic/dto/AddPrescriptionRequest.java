package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record AddPrescriptionRequest(
        @NotBlank String medicationName,
        String dosage,
        String frequency,
        String duration,
        Integer quantity,
        Integer repeats,
        String instructions
) {}