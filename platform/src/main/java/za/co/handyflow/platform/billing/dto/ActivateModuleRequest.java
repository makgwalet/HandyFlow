package za.co.handyflow.platform.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateModuleRequest(
        @NotBlank String moduleKey,
        String discountCode
) {}