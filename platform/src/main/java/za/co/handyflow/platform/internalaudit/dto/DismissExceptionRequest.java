package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record DismissExceptionRequest(@NotBlank String resolutionNotes) {}
