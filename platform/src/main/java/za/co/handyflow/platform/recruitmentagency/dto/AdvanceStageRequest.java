package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;

public record AdvanceStageRequest(@NotBlank String toStage, String notes) {}