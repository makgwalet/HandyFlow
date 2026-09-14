package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAuditTestRequest(@NotBlank String procedure) {}
