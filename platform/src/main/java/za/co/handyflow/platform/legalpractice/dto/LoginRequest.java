package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

/** Renamed from {@code PortalLoginRequest} — same shape, matching AuditorPortalAuthController.LoginRequest's own confirmed naming. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
