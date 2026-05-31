package za.co.handyflow.platform.desk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateSlaPolicyRequest(
        @NotBlank          String priority,
        @Positive          int    firstResponseHours,
        @Positive          int    resolutionHours
) {}
