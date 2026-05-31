package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePractitionerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String specialty,
        String hpcsaNumber,
        String practiceNumber,
        String phone,
        String email
) {}