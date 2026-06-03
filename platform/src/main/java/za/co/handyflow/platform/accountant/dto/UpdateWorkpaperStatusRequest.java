package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateWorkpaperStatusRequest(
        @NotBlank String status,
        UUID reviewedBy
) {
}
