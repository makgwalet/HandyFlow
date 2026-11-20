package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTrustSettingsRequest(@NotNull Boolean selfApprovalAllowed, @NotBlank String reason) {}
