package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** scope is MATTER (give matterId) or CLIENT (give clientId). */
public record CreateWallRequest(
        @NotBlank String scope,
        UUID matterId,
        UUID clientId,
        @NotNull UUID userId,
        String userName,
        @NotBlank String reason) {
}
