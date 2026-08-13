package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalLoginRequest(@NotBlank String email, @NotBlank String password) {}