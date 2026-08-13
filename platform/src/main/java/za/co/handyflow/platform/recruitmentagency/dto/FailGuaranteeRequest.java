package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;

public record FailGuaranteeRequest(@NotBlank String reason) {}