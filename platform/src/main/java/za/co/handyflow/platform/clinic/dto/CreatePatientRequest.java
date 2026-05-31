package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreatePatientRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String idNumber,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String email,
        String emergencyContactName,
        String emergencyContactPhone
) {}