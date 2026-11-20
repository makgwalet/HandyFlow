package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record LiftWallRequest(@NotBlank String reason) {
}
