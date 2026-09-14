package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignEngagementRoleRequest(
        @NotNull UUID userId,
        @NotBlank String role // HEAD_OF_INTERNAL_AUDIT | AUDIT_MANAGER | SENIOR_AUDITOR | AUDITOR | AUDIT_REVIEWER
) {}
