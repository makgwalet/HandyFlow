package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLpAttorneyRequest(
        @NotBlank String name,
        String email,
        String phone,
        @NotBlank String role,
        String admissionNumber,
        BigDecimal hourlyRate,
        UUID employeeId
) {}
