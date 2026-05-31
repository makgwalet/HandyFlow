package za.co.handyflow.platform.admin.dto;
import jakarta.validation.constraints.NotBlank;

public record AdminTotpRequest(
        @NotBlank String partialToken,
        @NotBlank String code            // 6-digit TOTP code
) {}