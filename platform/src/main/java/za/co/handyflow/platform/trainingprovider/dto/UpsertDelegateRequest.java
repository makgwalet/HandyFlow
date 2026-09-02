package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertDelegateRequest(
        @NotBlank String fullName,
        String idNumber,
        String email,
        String phone,
        String jobTitle
) {}
