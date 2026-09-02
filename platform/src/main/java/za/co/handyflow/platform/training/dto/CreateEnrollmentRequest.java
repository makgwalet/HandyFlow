package za.co.handyflow.platform.training.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotNull UUID employeeId,
        String notes
) {}
