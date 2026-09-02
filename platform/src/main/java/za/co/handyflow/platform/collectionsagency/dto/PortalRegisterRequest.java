package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalRegisterRequest(@NotBlank String inviteToken, @NotBlank String password, @NotBlank String fullName) {}
