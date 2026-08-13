package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalRegisterRequest(@NotBlank String inviteToken, @NotBlank String password, @NotBlank String fullName) {}