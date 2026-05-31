package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectProofRequest(
        @NotBlank String reason
) {}