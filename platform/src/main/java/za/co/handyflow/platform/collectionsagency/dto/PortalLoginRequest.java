package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalLoginRequest(@NotBlank String email, @NotBlank String password) {}
