package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnResourceRequest(
        @NotBlank String condition,      // GOOD | DAMAGED | MISSING
        String notes
) {}
