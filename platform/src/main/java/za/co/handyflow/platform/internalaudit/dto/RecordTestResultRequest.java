package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordTestResultRequest(
        @NotBlank String result, // PASS | FAIL | EXCEPTION
        String notes
) {}
