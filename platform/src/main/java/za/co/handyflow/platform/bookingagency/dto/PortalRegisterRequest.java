package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalRegisterRequest(@NotBlank String inviteToken, @NotBlank String password, @NotBlank String fullName) {}