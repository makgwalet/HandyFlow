package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordManagementResponseRequest(@NotBlank String response) {}
