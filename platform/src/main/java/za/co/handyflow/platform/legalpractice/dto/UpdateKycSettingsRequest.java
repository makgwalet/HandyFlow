package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateKycSettingsRequest(boolean enforce, @NotBlank String reason) {
}
