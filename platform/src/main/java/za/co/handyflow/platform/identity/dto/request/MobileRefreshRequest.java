package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobileRefreshRequest(@NotBlank(message = "Refresh token is required") String refreshToken, @Size(max = 100) String deviceId) {}
