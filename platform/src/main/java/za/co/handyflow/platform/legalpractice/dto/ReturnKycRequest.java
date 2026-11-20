package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnKycRequest(@NotBlank String reason) {
}
