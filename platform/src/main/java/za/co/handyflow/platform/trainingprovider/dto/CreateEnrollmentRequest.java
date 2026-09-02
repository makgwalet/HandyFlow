package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotNull UUID delegateId,
        String notes
) {}
